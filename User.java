import java.util.List;

public class User {

    private String username;
    private String email;
    private List<PantryItem> pantryItems;
    private List<Recipe> favoriteRecipes;

    public User() {
    }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }
}
