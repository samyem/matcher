package challenge;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;

import challenge.IndexService.SearchResult;

/**
 * Match products with listings.
 * 
 * @author samyem@samyem.com
 *
 */
public class ProductMatcher {
	private final Pattern keywordUnderScorePattern = Pattern.compile("\"[\\s|\\_]\"");
	private final Pattern keywordDashPattern = Pattern.compile("\"[\\s|\\-]\"");
	private final Pattern titleSplitPattern = Pattern.compile("[^A-Za-z0-9\\.\\-]");

	private final IndexService index;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private int productCounter = 0;

	private List<Product> products = new ArrayList<>();
	private String resultsFilePath;

	public ProductMatcher(String productsFilePath, String listingsFilePath, String resultsFilePath) throws IOException {
		this.resultsFilePath = resultsFilePath;

		index = new IndexService();

		indexProducts(productsFilePath);
		index.initSearch();

		long start = System.currentTimeMillis();

		System.out.println(productCounter + " products indexed");

		matchListings(listingsFilePath);

		long span = System.currentTimeMillis() - start;
		System.out.println("Match completed in " + span + "ms. Output written to " + resultsFilePath);
	}

	private void indexProducts(String productsFilePath) {
		try (Stream<String> stream = Files.lines(Paths.get(productsFilePath))) {
			stream.forEach(line -> {
				try {
					Product product = objectMapper.readValue(line, Product.class);
					index.indexProduct(productCounter, product);
					extractKeywords(product);
					products.add(product);
					productCounter++;
				} catch (IOException e) {
					System.err.println("Unable to parse product line " + line);
					e.printStackTrace();
				}
			});

		} catch (IOException e) {
			throw new RuntimeException("Unable to read products", e);
		}
	}

	private void extractKeywords(Product product) {
		String product_name = product.getProduct_name();
		String whitespaced = product_name.trim();

		Pattern regex;
		if (whitespaced.contains("_")) {
			regex = keywordUnderScorePattern;
		} else {
			regex = keywordDashPattern;
		}

		String[] splits = regex.split(whitespaced);
		Set<String> keyWords = new HashSet<>(splits.length);
		product.setKeywords(keyWords);

		// all CAPS and words with numbers become keyword
		for (String word : splits) {
			if (word.isEmpty()) {
				continue;
			}

			final int sz = word.length();
			boolean hasLower = false, hasNumber = false;

			for (int i = 0; i < sz; i++) {
				char c = word.charAt(i);
				if (Character.isLowerCase(c)) {
					hasLower = true;
				}
				if (Character.isDigit(c)) {
					hasNumber = true;
				}
			}

			if (hasNumber || !hasLower) {
				keyWords.add(word.toUpperCase());
			}
		}
	}

	/**
	 * Match product listing for the listings contained in the given path
	 * 
	 * @param listingsFilePath
	 */
	private void matchListings(String listingsFilePath) {
		Map<Product, Result> productResult = new HashMap<>(products.size());

		try (Stream<String> stream = Files.lines(Paths.get(listingsFilePath))) {
			stream.forEach(line -> {
				try {
					Listing listing = objectMapper.readValue(line, Listing.class);
					List<SearchResult> results = index.searchMatches(listing);

					for (SearchResult r : results) {
						Product product = products.get(r.index);

						if (!matchKeyword(product, listing)) {
							continue;
						}

						Result result = productResult.get(product);
						if (result == null) {
							result = new Result();
							result.setProduct_name(product.getProduct_name());
							productResult.put(product, result);
						}

						result.getListings().add(listing);
					}
				} catch (IOException e) {
					System.err.println("Unable to parse listing line " + line);
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			throw new RuntimeException("Unable to read listing", e);
		}

		writeResults(productResult.values());
	}

	/**
	 * Secondary match against product keywords
	 * 
	 * @param product
	 * @param listing
	 * @return
	 */
	private boolean matchKeyword(Product product, Listing listing) {
		String[] titleParts = titleSplitPattern.split(listing.getTitle().toUpperCase());

		Set<String> set = new HashSet<>(titleParts.length);
		for (String p : titleParts) {
			if (!p.isEmpty()) {
				set.add(p);
			}
		}

		for (String keyword : product.getKeywords()) {
			if (!set.contains(keyword)) {
				return false;
			}
		}

		return true;
	}

	private void writeResults(Collection<Result> results) {
		ObjectWriter jsonWriter = objectMapper.writerFor(Result.class);

		Path path = Paths.get(resultsFilePath);
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			throw new RuntimeException("Unable to delete old results", e);
		}

		try (Writer writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE)) {
			SequenceWriter writeValues = jsonWriter.writeValues(writer);

			for (Result result : results) {
				writeValues.write(result);
				writer.write("\n");
			}

		} catch (IOException e) {
			throw new RuntimeException("Unable to write results to " + resultsFilePath, e);
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("Usage: listingsPath productsPath resultsPath");
			return;
		}

		new ProductMatcher(args[0], args[1], args[2]);
	}
}
