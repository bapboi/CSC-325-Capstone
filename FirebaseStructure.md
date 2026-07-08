# SmartPantry Firebase Structure
## Firestore Collections
### users
Stores user account information.
### pantryItems
Stores pantry ingredients.
Fields:
- userID
- name
- category
- quantity
- unit
- expirationDate
- detectedByAI
- photoFileName
- photoStoragePath
### shoppingLists
Stores user shopping list items.
### recipes
Stores recipe information.
### favoriteRecipes
Stores recipes saved by users.
Fields:
- apiRecipeId
- recipeID
- userID
- imageURL
- createdAt
- savedAt
## Firebase Storage
ingredientImage/{userID}/{imageName}
Example:
ingredientImage/testuser/tomato.jpg
## Notes
- Firebase Authentication is used for user login and identification.
- Firestore stores application data.
- Firebase Storage stores ingredient images.
- Pantry items reference images through photoStoragePath.
- Recipe information may be populated through the recipe API.
