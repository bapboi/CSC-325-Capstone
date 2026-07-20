# SmartPantry Firebase Structure

## Firestore Collections

### users
Stores information about each user.

Fields:
- email
- name
- createdAt

### pantryItems
Stores pantry ingredients for each user.

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
Stores the shopping list for each user.

Fields:
- userID
- name
- quantity
- unit
- checked

### recipes
Stores recipe information.

Fields:
- recipeName
- category
- cookTime
- difficulty
- servings
- ingredients
- requiredIngredients
- instructions

### savedRecipes
Stores recipes saved by users.

Fields:
- userID
- name
- description
- prepTime
- cookTime
- difficulty
- servings
- sourceLink
- imageUrl
- ingredients
- pantryIngredients
- missingIngredients
- instructions

## Firebase Storage

Storage Path:

ingredientImage/{userID}/{imageName}

Example:

ingredientImage/testuser/tomato.jpg

## Notes

- Firestore is used to store the application's data.
- Firebase Storage is used to store images of pantry ingredients.
- The `pantryItems` collection works with the `Ingredient` model.
- The `shoppingList` collection works with the `ShoppingItem` model.
- The `recipes` collection stores recipe information used by the app.
- The `savedRecipes` collection stores recipes that a user has saved.
- Recipe information may come from the recipe API.

## Security

### Firestore Rules

Only authenticated users can read and write Firestore data.

```javascript
rules_version = '2';

service cloud.firestore {
  match /databases/{database}/documents {
    match /{document=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

### Storage Rules

Only authenticated users can read and write files in Firebase Storage.

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /{allPaths=**} {
      allow read, write: if request.auth != null
                          && request.auth.uid == userId;
    }
  }
}
```

## Authentication

- Firebase Authentication is used for logging users into the application.
- Each user is identified by their Firebase UID.
- Firestore and Storage use these security rules to control access.
