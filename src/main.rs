#[macro_use]
extern crate log;
mod index;
mod metrics;
mod moviedb_importer;
use serde_json;
use std::fs::File;
use std::io::prelude::*;
use std::io::BufReader;
use std::path::PathBuf;

fn main() {
    let base_path = PathBuf::from(r"./index");

    let schema = read_schema("./schemata/movies.json".to_string());

    let mut catalog = index::IndexCatalog::new(base_path).unwrap();
    catalog.create_index("movies".to_string(), schema).unwrap();
    let index = catalog.get_index(&"movies".to_string()).unwrap();
    let articles = moviedb_importer::reader("datasets/movies.txt");

    let docs = index.add_documents(&articles);
    match docs {
        Ok(()) => println!("Documents added"),
        Err(e) => println!("can’t add documents: {:?}", e),
    }
    let benchmark_data =
        moviedb_importer::benchmarkreader("datasets/movies-benchmark.txt").unwrap();
    evaluate(benchmark_data, index);
}
fn evaluate(
    benchmark_data: std::collections::HashMap<std::string::String, std::vec::Vec<i32>>,
    index: &mut index::IndexHandle,
) {
    let fields = vec!["title", "body", "fulltext"];

    for field in fields {
        let mut sum_p_at_3 = 0.0;
        let mut sum_p_at_r = 0.0;
        let mut sum_ap = 0.0;

        println!("####### Field: {} ", field);

        for (key, relevant_docs_vec) in &benchmark_data {
            let relevant_docs = relevant_docs_vec;
            let mut retrieved_ids = Vec::new();
            debug!("Query: {:?}", key);
            let retrieved_docs = index
                .query(&key.to_string(), 100, &field.to_string())
                .unwrap();

            let id_field = tantivy::schema::Field(0);
            let title_field = tantivy::schema::Field(1);
            let num_res = retrieved_docs.len();
            for doc in retrieved_docs {
                let id = doc.1.get_first(id_field).unwrap().u64_value() as i32;
                let title = doc.1.get_first(title_field).unwrap();
                debug!("Title {:?} ID: {:?} Score : {:?}", title, id, doc.0);
                retrieved_ids.push(id as i32);
            }
            debug!("Retrieved Ids: {:?}", retrieved_ids.sort());
            debug!("Results: {:?}", num_res);
            let p_at_3 = metrics::p_at_k(retrieved_ids.clone(), relevant_docs.clone(), 3);
            debug!("p@3: {}", p_at_3);
            sum_p_at_3 += p_at_3;
            debug!("sum_p@3: {}", sum_p_at_3);
            let r = relevant_docs.len();
            let p_at_r = metrics::p_at_k(retrieved_ids.clone(), relevant_docs.clone(), r);
            debug!("p@r: {}", p_at_r);
            sum_p_at_r += p_at_r;
            debug!("sum_p@r: {}", sum_p_at_r);

            let ap = metrics::ap(retrieved_ids, relevant_docs.clone());
            debug!("ap: {}", ap);
            sum_ap += ap;
            debug!("sum_ap: {}", sum_ap);
        }
        let mp_at_3 = sum_p_at_3 / benchmark_data.len() as f32;
        let mp_at_r = sum_p_at_r / benchmark_data.len() as f32;
        let map = sum_ap / benchmark_data.len() as f32;
        println!(
            "MP@3: {} MP@R: {} MAP: {}",
            mp_at_3,
            mp_at_r,
            map.to_string()
        );
    }
}

fn read_schema(path: String) -> tantivy::schema::Schema {
    let file = File::open(path).unwrap();
    let mut buf_reader = BufReader::new(file);
    let mut contents = String::new();
    buf_reader.read_to_string(&mut contents).unwrap();
    let schema: tantivy::schema::Schema =
        serde_json::from_str(&contents).expect("JSON was not well-formatted");
    schema
}
