/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package lucene.ir.evaluation;

import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.index.DirectoryReader;

public class App {

    public static void main(String[] args) {
			try {
				if (args.length > 0 && args[0].equals("index")) {
					List<String[]> movies = splitFile(FileSystems.getDefault().getPath("..", "datasets", "movies.txt"));
					if (movies != null) {
						indexMovies(movies);
					}
				} else if (args[0].equals("evaluate")) {
					evaluateIndex();
				} else {
					System.out.println("Please pass index or evaluate argument.");
				}

			} catch (IOException | ParseException e) {
				System.out.println(e.getMessage());
			}	
    }

    private static List<String[]> splitFile(Path path) {
        List<String> lines = null;
        try {
            lines = Files.readAllLines(path);
        } catch (java.io.IOException e) {
            System.out.println(e.getMessage());
        }
        List<String[]> splitLines = new ArrayList<>();
        for (String line : lines) {
            splitLines.add(line.split("\t"));
        }
        return splitLines;
    }

    private static void indexMovies(List<String[]> movies) throws IOException {
        Directory index = FSDirectory.open(FileSystems.getDefault().getPath("data"));
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(index, config);

		Document doc = null;
		int id = 1;
		for (String[] movie : movies) {
			doc = new Document();
			doc.add(new StringField("id", String.valueOf(id), Field.Store.YES));
			doc.add(new TextField("title", movie[0], Field.Store.YES));
			doc.add(new TextField("body", movie[1], Field.Store.YES));
			doc.add(new TextField("fulltext", movie[0] + System.lineSeparator() + movie[1], Field.Store.YES));
			writer.addDocument(doc);
			id++;
		}
		writer.close();
    }

	private static void evaluateIndex() throws IOException, ParseException {
        Directory index = FSDirectory.open(FileSystems.getDefault().getPath("data"));
        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
        StandardAnalyzer analyzer = new StandardAnalyzer();

		List<String[]> benchmarks = splitFile(FileSystems.getDefault().getPath("..", "datasets", "movie-datset", "movies-benchmark.txt"));
		String[] ids;
		HashMap<String, HashSet<String>> benchmarkMap = new HashMap<>();

		for (String[] benchmark : benchmarks) {
			ids = benchmark[1].split(" ");
			HashSet<String> hashIds = new HashSet<String>(Arrays.asList(ids));
			benchmarkMap.put(benchmark[0], hashIds);
		}

        String[] queries = { "title", "body", "fulltext" };

        for (String query : queries) {
            double sumPat3 = 0.0;
            double sumPatR = 0.0;
            double sumAP = 0.0;

            System.out.println("-----------------------------------------------");
            System.out.println("       Query: "+ query);
            System.out.println("-----------------------------------------------");

            for (Map.Entry<String, HashSet<String>> benchmark : benchmarkMap.entrySet()) {
                String searchTerm = benchmark.getKey();
                HashSet<String> hashIds = benchmark.getValue();
                System.out.println("Searching for: " + searchTerm);
                Query q = new QueryParser(query, analyzer).parse(searchTerm);
                TopDocs docs = searcher.search(q, 100);
                ScoreDoc[] hits = docs.scoreDocs;
                Document[] results = new Document[hits.length];
                ScoreDoc hit;
                for (int i = 0; i < hits.length; i++) {
                    hit = hits[i];
                    results[i] = searcher.doc(hit.doc);	
                }

                double precAt3 = precisionAtK(results, hashIds, 3);
                sumPat3 += precAt3;
                System.out.println("Prec@3: " + precAt3);
                double precAtR = precisionAtK(results, hashIds, hashIds.size());
                sumPatR += precAtR;
                System.out.println("Prec@r: " + precAtR);
                double ap = averagePrecision(results, hashIds);
                sumAP += ap;
                System.out.println("AP: " + ap);
            }

            double mpAt3 = sumPat3 / benchmarkMap.size();
            double mpAtR = sumPatR / benchmarkMap.size();
            double map = sumAP / benchmarkMap.size();
            System.out.println("MP@3: " + mpAt3 + " MP@R: " + mpAtR + " MAP: " + map);

        }
    }

	/**
	 * Precision at k
	**/
	private static double precisionAtK(Document[] results, HashSet<String> testSet, int k) {
		double counter = 0.0;
		double p = 0.0;
		if (k == 0) {
			return 0.0;
		}
		for (int i = 0; i < k; i++) {
			if (results.length > i && testSet.contains(results[i].get("id"))) {
				p += 1.0;
				counter += 1.0;
			} else {
				counter += 1.0;
			}
		}
		return p / counter;	
	}

	/**
	 * Average Precision
	**/
	private static double averagePrecision(Document[] results, HashSet<String> testSet) {
		double ap = 0.0;
		if (testSet.size() < 1) {
			return 0.0;
		}
	
		for (int i = 0; i < results.length; i++) {
			if (testSet.contains(results[i].get("id"))) {
				ap += precisionAtK(results, testSet, i+1);
			}
		}
		
		return ap / testSet.size();
	}
}
