public class Ingredient {
    private String name;
    private String category;
    private int quantity;
    private String unit;
    public Ingredient() {
    }

    public Ingredient(String name, String category, int quantity, String unit) {
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unit = unit;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }
}
