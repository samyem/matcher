package challenge;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;

public class IndexService {
	private static final int HITS = 1;

	private final Analyzer analyzer = new EnglishAnalyzer();

	private final MMapDirectory directory;

	private IndexWriter indexWriter;
	private IndexReader indexReader;
	private IndexSearcher searcher;

	public IndexService() throws IOException {
		try {
			directory = new MMapDirectory(Paths.get("index"));
		} catch (IOException e) {
			throw new IOException("Unable to initialize index", e);
		}
		initIndex();
		prepareNewIndex();
	}

	private void initIndex() {
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		try {
			indexWriter = new IndexWriter(directory, config);
		} catch (IOException e) {
			throw new RuntimeException("Unable to open index writer", e);
		}
	}

	private void prepareNewIndex() {
		try {
			indexWriter.deleteAll();
			indexWriter.commit();
		} catch (IOException e) {
			throw new RuntimeException("Unable to clear indexes", e);
		}
	}

	/**
	 * Commit all writes and prepare for search mode
	 */
	public void initSearch() {
		try {
			indexWriter.commit();
			indexReader = DirectoryReader.open(directory);
			searcher = new IndexSearcher(indexReader);
		} catch (IOException e) {
			throw new RuntimeException("Cannot create index reader", e);
		}
	}

	public void indexProduct(int index, Product product) {
		Document doc = new Document();

		addField(doc, "name", product.getProduct_name());
		addField(doc, "family", product.getFamily());
		addField(doc, "model", product.getModel());
		addField(doc, "manufacturer", product.getManufacturer());
		doc.add(new StoredField("index", index));

		try {
			indexWriter.addDocument(doc);
		} catch (IOException e) {
			throw new RuntimeException("Unable to add index doc", e);
		}

	}

	private void addField(Document doc, String field, String value) {
		if (value == null) {
			return;
		}

		value = value.trim().replaceAll("\\W", " ");

		StandardTokenizer tokenStream = new StandardTokenizer();
		tokenStream.setReader(new StringReader(value));
		TextField txtField = new TextField(field, tokenStream);
		doc.add(txtField);
	}

	static class SearchResult {
		int index;
		float score;
	}

	public List<SearchResult> searchMatches(Listing listing) {
		BooleanQuery.Builder builder = new BooleanQuery.Builder();

		BooleanQuery.Builder titleBuilder = new BooleanQuery.Builder();

		String title = listing.getTitle();
		String manufacturer = listing.getManufacturer();

		title = title.trim().replaceAll("\\W", " ");

		for (String word : title.split(" ")) {
			if (!word.trim().isEmpty()) {
				titleBuilder.add(new TermQuery(new Term("name", word)), Occur.SHOULD);
				titleBuilder.add(new TermQuery(new Term("family", word)), Occur.SHOULD);
				titleBuilder.add(new TermQuery(new Term("model", word)), Occur.SHOULD);
			}
		}

		builder.add(titleBuilder.build(), Occur.MUST);

		BooleanQuery.Builder manufacturerBuilder = new BooleanQuery.Builder();
		for (String word : manufacturer.split(" ")) {
			if (!word.trim().isEmpty()) {
				TermQuery query = new TermQuery(new Term("manufacturer", word));
				BooleanClause clause = new BooleanClause(query, Occur.SHOULD);
				manufacturerBuilder.add(clause);
			}
		}
		builder.add(manufacturerBuilder.build(), Occur.MUST);

		TopDocs docs;
		try {
			BooleanQuery build = builder.build();
			docs = searcher.search(build, HITS);
			ScoreDoc[] hits = docs.scoreDocs;

			List<SearchResult> list = new ArrayList<>(hits.length);
			for (ScoreDoc hit : hits) {
				Document d = searcher.doc(hit.doc);
				String id = d.get("index");
				SearchResult result = new SearchResult();
				result.index = Integer.parseInt(id);
				result.score = hit.score;
				list.add(result);
			}

			return list;
		} catch (IOException e) {
			throw new RuntimeException("Cannot search", e);
		}
	}

}
