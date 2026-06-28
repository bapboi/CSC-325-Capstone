public class PantryItem {
    private Ingredient ingredient;

    public PantryItem() {
    }
    public PantryItem(Ingredient ingredient) {
        this.ingredient = ingredient;
    }
    public Ingredient getIngredient() {
        return ingredient;
    }
    public void setIngredient(Ingredient ingredient) {
        this.ingredient = ingredient;
    }
}
