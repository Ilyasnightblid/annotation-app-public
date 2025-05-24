# AnnotationApp - Application de Gestion d'Annotations

AnnotationApp est une application web développée avec Spring Boot pour la gestion de tâches d'annotation de texte. Elle permet aux administrateurs de créer des datasets, d'importer des paires de textes, et d'assigner des tâches à des annotateurs. Les annotateurs peuvent ensuite se connecter pour annoter les textes qui leur sont assignés.

## Fonctionnalités Clés

**Côté Administrateur :**
*   🔒 Authentification sécurisée.
*   📊 Dashboard avec statistiques (nombre de datasets, annotateurs, progression).
*   ➕ Créer des datasets :
    *   Uploader un fichier CSV contenant des paires de textes (`Text1`, `Text2`).
    *   Saisir un nom de dataset et une description.
    *   Définir une liste de classes/catégories possibles pour l'annotation.
*   📋 Voir la liste des datasets avec leur taux de complétion.
*   📄 Voir les détails d’un dataset :
    *   Afficher les paires de texte (Text1, Text2).
    *   Voir la liste des annotateurs affectés et leur travail.
    *   Désaffecter un annotateur d'une tâche PENDANTE.
*   👥 Gestion des Annotateurs :
    *   Ajouter de nouveaux comptes annotateurs (mot de passe auto-généré).
    *   Lister les annotateurs.
*   ✍️ Affecter des annotateurs à un dataset (distribution automatique des paires de texte).

**Côté Annotateur :**
*   🔒 Authentification sécurisée.
*   👤 Profil utilisateur avec possibilité de changer son mot de passe.
*   📋 Voir la liste de ses tâches (les paires de texte qui lui sont affectées).
*   ✏️ Annoter chaque couple : choisir une classe parmi celles possibles pour le dataset.

## Technologies Utilisées

*   **Backend :**
    *   Java 17+
    *   Spring Boot 3.x
    *   Spring MVC
    *   Spring Data JPA (avec Hibernate)
    *   Spring Security
    *   Maven (pour la gestion des dépendances et le build)
*   **Frontend :**
    *   Thymeleaf
    *   Bootstrap 5
    *   HTML5, CSS3, JavaScript (minimal pour Chart.js)
    *   Font Awesome (pour les icônes)
    *   Chart.js (pour les graphiques du dashboard)
*   **Base de Données :**
    *   MySQL (ou MariaDB) - *Configuré pour une base de données nommée `annotation_app_db`*
    *   (Initialement développé avec H2 en mémoire pour prototypage rapide)
*   **Utilitaires :**
    *   Lombok
    *   Apache Commons CSV (pour l'import de datasets)
    *   Apache Commons Lang (pour la génération de mot de passe)

## Prérequis

*   JDK 17 ou plus récent.
*   Apache Maven 3.6+ (ou utilise le Maven Wrapper `mvnw`).
*   Un serveur MySQL (ou MariaDB) en cours d'exécution.

## Installation et Lancement

1.  **Cloner le dépôt :**
    ```bash
    git clone https://github.com/Ilyasnightblid/annotation-app-public.git 
    # Remplace par l'URL de ton nouveau dépôt public
    cd annotation-app-public 
    # Ou le nom de ton nouveau dossier de projet
    ```

2.  **Configurer la base de données MySQL :**
    *   Assure-toi que ton serveur MySQL est lancé.
    *   Crée une base de données (par exemple, `annotation_app_db`):
      ```sql
      CREATE DATABASE annotation_app_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
      ```
    *   (Optionnel mais recommandé) Crée un utilisateur dédié pour l'application et donne-lui les privilèges sur cette base.

3.  **Configurer `application.properties` :**
    *   À la racine du projet, copie `src/main/resources/application.properties.example` vers `src/main/resources/application.properties`.
    *   Modifie `src/main/resources/application.properties` pour y mettre tes identifiants de connexion MySQL :
      ```properties
      spring.datasource.url=jdbc:mysql://localhost:3306/annotation_app_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
      spring.datasource.username=VOTRE_USER_MYSQL
      spring.datasource.password=VOTRE_MOT_DE_PASSE_MYSQL
      ```

4.  **Compiler et Lancer l'application avec Maven :**
    ```bash
    # Depuis la racine du projet
    ./mvnw spring-boot:run 
    # Ou si tu as Maven installé globalement: 
    # mvn spring-boot:run
    ```
    Alternativement, tu peux importer le projet dans IntelliJ IDEA (ou un autre IDE) comme un projet Maven et le lancer depuis l'IDE.

5.  **Accéder à l'application :**
    Ouvre ton navigateur et va sur `http://localhost:8080`.

## Identifiants par Défaut

Au premier lancement (si la base est vide), les utilisateurs suivants sont créés :
*   **Administrateur :**
    *   Username : `admin`
    *   Password : `admin123`
*   **Annotateur de Test :**
    *   Username : `annotator1`
    *   Password : `annotator123`

Les mots de passe pour les nouveaux annotateurs créés par l'admin sont auto-générés. En mode développement, ils sont affichés dans la console Spring Boot au moment de la création.

## Structure du Projet (Principaux Dossiers Backend)

*   `src/main/java/com/example/annotationapp/`
    *   `AnnotationappApplication.java`: Point d'entrée de Spring Boot.
    *   `config/`: Configurations Spring (Sécurité, Initialisation des données).
    *   `controller/`: Contrôleurs Spring MVC (gèrent les requêtes HTTP).
    *   `dto/`: Data Transfer Objects (pour les formulaires, exports, etc.).
    *   `entity/`: Entités JPA (mapping avec les tables de la BDD).
    *   `repository/`: Interfaces Spring Data JPA (accès aux données).
    *   `service/`: Classes de service contenant la logique métier.
    *   `util/`: Classes utilitaires (ex: génération de mot de passe).
*   `src/main/resources/`
    *   `application.properties`: Fichier de configuration principal de Spring Boot.
    *   `static/`: Ressources statiques (CSS, JavaScript, images).
    *   `templates/`: Templates Thymeleaf (vues HTML).

## Licence

Ce projet est distribué sous la licence XYZ. (Précise ta licence si tu en as une, sinon tu peux mettre "Tous droits réservés" ou choisir une licence open source comme MIT).

---

*Dernière mise à jour : JJ/MM/AAAA*
