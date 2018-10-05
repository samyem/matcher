package challenge;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Product {
	private String product_name, manufacturer, family, model;

	@JsonProperty("announced-date")
	private String announcedDate;

	private Set<String> keywords;

	public String getManufacturer() {
		return manufacturer;
	}

	public void setManufacturer(String manufacturer) {
		this.manufacturer = manufacturer;
	}

	public String getProduct_name() {
		return product_name;
	}

	public void setProduct_name(String product_name) {
		this.product_name = product_name;
	}

	public String getFamily() {
		return family;
	}

	public void setFamily(String family) {
		this.family = family;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getAnnouncedDate() {
		return announcedDate;
	}

	public void setAnnouncedDate(String announcedDate) {
		this.announcedDate = announcedDate;
	}

	public Set<String> getKeywords() {
		return keywords;
	}

	public void setKeywords(Set<String> keywords) {
		this.keywords = keywords;
	}

}
