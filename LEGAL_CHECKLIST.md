# LEGAL_CHECKLIST — NutriSport

| Version de la checklist | Date | Auteur |
|---|---|---|
| 0.1 | 2026-10-20 | [À COMPLÉTER] |

> **À repasser intégralement :**
> - avant **chaque mise en production** (nouvelle version Play Store) ;
> - à **chaque évolution majeure de l'IA utilisée** (changement de fournisseur, de modèle, de données envoyées, de finalité) ;
> - à **chaque évolution des lois / politiques** (RGPD, lignes directrices CNIL, AI Act, Code de la consommation, règles Google Play) ;
> - et au minimum **une fois par an**.
>
> Cette checklist n'est pas un avis juridique. Les points marqués ⚖️ doivent être validés par un avocat ou le DPO.

## 0. Identification de la revue

- [ ] Version de l'app revue : `versionName` = ______ / `versionCode` = ______
- [ ] Date de la revue : ______
- [ ] Motif : ☐ release ☐ changement IA ☐ changement légal ☐ revue annuelle
- [ ] Modèle / fournisseur IA en production : ______ (date de la dernière vérification de ses conditions et du DPA : ______)

## 1. Documents légaux (versionnés et datés)

Chaque document contient en en-tête : **version**, **date d'entrée en vigueur**, **historique des modifications**.

| Document | Fichier | Version | Date | Publié sur le web (URL) | Revu ⚖️ |
|---|---|---|---|---|---|
| CGU | `docs/legal/CGU.md` | | | | ☐ |
| CGV (abonnement) | `docs/legal/CGV.md` | | | | ☐ |
| Mentions légales (LCEN) | `docs/legal/MENTIONS_LEGALES.md` | | | | ☐ |
| Politique de confidentialité | `docs/legal/CONFIDENTIALITE.md` | | | | ☐ |
| AIPD | `docs/AIPD_brouillon.md` | | | — | ☐ |

- [ ] La copie dans l'app (`app/src/main/assets/legal/`) est **identique** à `docs/legal/` et à la version web.
- [ ] Si un document a changé de façon substantielle, l'app **redemande l'acceptation** (CGU/CGV) ou **le consentement** (confidentialité/IA) au lancement suivant.
- [ ] Les champs `[À COMPLÉTER]` sont tous remplis (éditeur, SIRET, adresse, directeur de publication, hébergeur, contact, médiateur).

## 2. RGPD — données de santé (art. 9)

- [ ] **Registre des traitements** à jour (finalités, catégories de données, destinataires, durées, transferts).
- [ ] **Consentement explicite** santé : case non pré-cochée, séparée des CGU, texte clair, lien vers la politique, journalisé (`ConsentRecord` avec version du document).
- [ ] Consentements **séparés** : santé / IA / rapports de plantage. Chacun est **retirable** aussi facilement que donné (Paramètres > Consentements).
- [ ] **Minimisation** : aucune nouvelle donnée collectée sans justification. Nouvelle donnée ajoutée dans cette version ? ☐ non ☐ oui → mettre à jour la politique, Data safety et l'AIPD.
- [ ] Données de santé **uniquement sur l'appareil**, dans une base chiffrée (SQLCipher + Keystore). Aucune nouvelle synchronisation cloud sans mise à jour de l'AIPD ⚖️.
- [ ] **Durées de conservation** conformes à la politique (compte inactif, logs serveur, IA).
- [ ] **Droits** testés manuellement sur cette version :
  - [ ] accès / portabilité : l'export JSON contient toutes les données ;
  - [ ] effacement dans l'app : base, préférences et compte Firebase supprimés ;
  - [ ] effacement via la page web publique ;
  - [ ] rectification : profil modifiable ;
  - [ ] retrait du consentement.
- [ ] **Sous-traitants et transferts hors UE** listés et à jour (Google/Firebase, fournisseur IA) : DPA signés, mécanisme de transfert (DPF / CCT) vérifié ⚖️.
- [ ] **AIPD** relue si la version modifie les données, les finalités, l'IA ou les sous-traitants ⚖️.
- [ ] Procédure de **violation de données** (notification CNIL sous 72 h) connue et à jour.

## 3. Sécurité

- [ ] Aucune donnée personnelle ou de santé dans les logs (recherche `Log.` hors `SafeLogger`, revue des messages `SafeLogger`).
- [ ] `network_security_config` : trafic en clair interdit.
- [ ] Sauvegardes cloud et transfert d'appareil désactivés pour les données (`data_extraction_rules.xml`).
- [ ] R8 activé en release. Aucun secret (clé API IA, etc.) dans l'APK ni dans git.
- [ ] Dépendances mises à jour, sans vulnérabilité connue critique.
- [ ] Analytics : aucun. Crashlytics (si ajouté) : désactivé tant que l'utilisateur n'y a pas consenti.

## 4. Santé — pas d'allégation, garde-fous actifs

- [ ] Mention « **ne remplace pas un médecin ni un diététicien, n'est pas un dispositif médical** » visible : onboarding, Suivi, choix du rythme, fiche Play.
- [ ] Aucune **allégation médicale** ni **promesse de résultat** (textes de l'app, fiche Play, captures, notifications, paywall). Interdit : « garanti », « soigne », « traite », « perdez X kg en Y jours ».
- [ ] Les durées et dates d'arrivée sont présentées comme des **estimations**.
- [ ] Garde-fous testés (tests unitaires verts) :
  - [ ] perte ≤ 1 % du poids par semaine ;
  - [ ] prise ≤ 0,5 % du poids par semaine ;
  - [ ] plancher calorique ;
  - [ ] IMC cible ≥ 18,5 ;
  - [ ] blocage des moins de 18 ans ;
  - [ ] pas de déficit en cas de grossesse ou d'allaitement.
- [ ] Les sources des recommandations (`docs/SOURCES.md`) ont été revérifiées si une agence (HAS, ANSES, OMS, NICE) a publié une mise à jour.
- [ ] Le statut **hors dispositif médical** (règlement MDR 2017/745) reste valable : aucune nouvelle fonction de diagnostic, traitement ou prédiction médicale ⚖️.

## 5. Allergènes et données alimentaires

- [ ] Avertissement allergènes visible sur l'onglet Repas, le détail d'un plat et la liste de courses.
- [ ] Les 14 allergènes de l'annexe II du règlement INCO (UE) n° 1169/2011 sont tous présents.
- [ ] Test « aucun allergène déclaré ne passe le filtre » vert (y compris pour les recettes IA).
- [ ] **Ciqual** : version utilisée = ______. Licence revérifiée. Attribution affichée (Paramètres > Sources et licences).
- [ ] Saisonnalité : source et licence revérifiées.
- [ ] `ASSETS_LICENSES.md` à jour (icônes, images, polices, bibliothèques open source).

## 6. IA (variantes de plats, Premium)

- [ ] Consentement IA séparé, retirable, journalisé. Sans consentement, aucun appel IA.
- [ ] Charge utile envoyée **minimisée** et identique à celle décrite dans la politique (aucun identifiant, poids ni âge).
- [ ] Toute recette IA est **revalidée localement** (nutriments Ciqual, allergènes, régimes) avant affichage.
- [ ] Étiquette « **Généré par IA** » visible (AI Act, art. 50, transparence).
- [ ] Aucun contenu de requête conservé côté serveur. Les logs du proxy ne contiennent pas la charge utile.
- [ ] Changement de modèle ou de fournisseur depuis la dernière revue ? ☐ non ☐ oui → revoir le DPA, la politique, l'AIPD et la fiche Data safety ⚖️.

## 7. Abonnement (Code de la consommation)

- [ ] Prix, durée, renouvellement automatique et modalités de résiliation affichés **avant** l'achat.
- [ ] Renonciation au droit de rétractation pour le contenu numérique (art. L221-28 13°) : accord exprès recueilli ⚖️.
- [ ] Lien « Gérer / résilier mon abonnement » vers Google Play accessible en un clic.
- [ ] **Médiateur de la consommation** désigné et indiqué dans les CGV et les mentions légales ⚖️.
- [ ] L'export et la suppression des données restent **gratuits** et hors paywall.

## 8. Google Play Console

- [ ] **targetSdk** conforme à l'exigence de l'année en cours (36 depuis le 31/08/2026). Prochaine échéance : ______.
- [ ] **Politique de confidentialité** : URL publique, active, identique à la version dans l'app.
- [ ] **Data safety** à jour avec `docs/DATA_SAFETY.md` (données collectées et partagées, chiffrement en transit, suppression possible).
- [ ] **Déclaration des applis de santé** (Health apps declaration) remplie et cohérente.
- [ ] **Suppression de compte** : dans l'app et via une URL web déclarée dans Play Console.
- [ ] **Classification du contenu** et **public cible** : 18 ans et plus. Pas de ciblage des enfants.
- [ ] Fiche Play : pas d'allégation santé, mention « pas un dispositif médical ».
- [ ] Bibliothèque Play Billing à la version minimale exigée.
- [ ] Politiques Play relues (centre de règles). Nouvelles règles depuis la dernière revue : ______.

## 9. Validation finale

- [ ] Points ⚖️ modifiés depuis la dernière revue → envoyés à l'avocat ou au DPO le ______, validés le ______.
- [ ] Revue signée par : ______ — date : ______

## Historique de la checklist

| Version | Date | Modifications |
|---|---|---|
| 0.1 | 2026-10-20 | Création (étape 0 du projet). |
