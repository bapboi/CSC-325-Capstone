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
- createdAt

### shoppingList
Stores user shopping list items.

### recipes
Stores recipe information.

### savedRecipes
Stores recipes saved by users.

Fields:
- apiRecipeId
- recipeID
- userID
- imageURL
- createdAt
- savedAt

## Firebase Storage
Storage Path:
ingredientImage/{userID}/{imageName}

Example:
ingredientImage/testuser/tomato.jpg

## Notes
- Firestore stores application data.
- Firebase Storage stores ingredient images associated with pantry items.
- Recipe information may be populated through the recipe API.

## Security

### Firestore Rules
- Read and write access is restricted to authenticated users.

### Storage Rules
- Read and write access is restricted to authenticated users.

## Authentication
- Firebase Authentication is used for user login and identification.
- User records are associated with Firebase UIDs.

