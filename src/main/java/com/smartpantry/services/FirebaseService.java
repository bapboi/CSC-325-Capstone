package com.smartpantry.services;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.smartpantry.model.Ingredient;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

public class FirebaseService {

  private static FirebaseService instance;
  private Firestore db;
  private boolean connected = false;

  private static final String PANTRY_COLLECTION = "pantryItems";
  private static final Path CONFIG_FILE = Paths.get(System.getProperty("user.home"), ".smartpantry",
      "config.properties");

  private FirebaseService() {
  }

  public static synchronized FirebaseService getInstance() {
    if (instance == null)
      instance = new FirebaseService();
    return instance;
  }

  public boolean isConnected() {
    return connected;
  }

  public String resolveSavedCredentialPath() {
    Path projectLocalKey = Paths.get("firebase-key.json");
    if (Files.exists(projectLocalKey)) {
      return projectLocalKey.toAbsolutePath().toString();
    }

    String envPath = System.getenv("SMARTPANTRY_FIREBASE_KEY");
    if (envPath != null && Files.exists(Paths.get(envPath)))
      return envPath;

    if (Files.exists(CONFIG_FILE)) {
      try (InputStream in = Files.newInputStream(CONFIG_FILE)) {
        Properties props = new Properties();
        props.load(in);
        String saved = props.getProperty("credentialPath");
        if (saved != null && Files.exists(Paths.get(saved)))
          return saved;
      } catch (IOException ignored) {
      }
    }
    return null;
  }

  public void saveCredentialPath(String path) throws IOException {
    Files.createDirectories(CONFIG_FILE.getParent());
    Properties props = new Properties();
    props.setProperty("credentialPath", path);
    try (var out = Files.newOutputStream(CONFIG_FILE)) {
      props.store(out, "SmartPantry config");
    }
  }

  public void initialize(String serviceAccountKeyPath) throws IOException {
    if (!FirebaseApp.getApps().isEmpty()) {
      db = FirestoreClient.getFirestore();
      connected = true;
      return;
    }
    try (FileInputStream serviceAccount = new FileInputStream(serviceAccountKeyPath)) {
      FirebaseOptions options = FirebaseOptions.builder()
          .setCredentials(GoogleCredentials.fromStream(serviceAccount))
          .build();
      FirebaseApp.initializeApp(options);
      db = FirestoreClient.getFirestore();
      connected = true;
    }
  }

  public List<Ingredient> getAllIngredients() throws ExecutionException, InterruptedException {
    List<Ingredient> result = new ArrayList<>();
    ApiFuture<QuerySnapshot> future = db.collection(PANTRY_COLLECTION).get();
    for (QueryDocumentSnapshot doc : future.get().getDocuments()) {
      Ingredient ingredient = doc.toObject(Ingredient.class);
      ingredient.setId(doc.getId());
      result.add(ingredient);
    }
    return result;
  }

  public String addIngredient(Ingredient ingredient) throws ExecutionException, InterruptedException {
    DocumentReference ref = db.collection(PANTRY_COLLECTION).document();
    ApiFuture<WriteResult> future = ref.set(ingredient.toMap());
    future.get();
    return ref.getId();
  }
}
