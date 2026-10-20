# Kalori — cadre d'usage personnel

| Version | Date |
|---|---|
| 1.0 | 2026-10-20 |

Kalori est une application **à usage strictement personnel** : installée sur mon téléphone, sans compte, sans serveur, sans publication sur le Play Store et sans monétisation.

## Ce qui ne s'applique pas
- **RGPD** : le traitement de données par une personne physique pour une activité strictement personnelle ou domestique est exclu du règlement (art. 2.2.c). Pas de consentement à recueillir, pas d'AIPD, pas de politique de confidentialité, pas de registre.
- **LCEN, CGU, CGV, mentions légales** : sans mise à disposition du public, rien à publier.
- **Règles Google Play** (Data safety, déclaration santé, suppression de compte, targetSdk) : non concerné tant que l'app n'est pas publiée.

## Ce qui reste valable
- [ ] **Sécurité de mes propres données** : base chiffrée (SQLCipher + Keystore), aucune sauvegarde cloud automatique, aucune donnée dans les logs, HTTPS uniquement.
- [ ] **Sauvegarde** : les données n'existent que sur le téléphone. Exporter régulièrement (Paramètres > Exporter) et garder le fichier en lieu sûr.
- [ ] **Garde-fous santé** conservés (plafonds de perte et de prise, plancher calorique, IMC cible ≥ 18,5) et avertissements : l'app ne remplace pas un médecin ni un diététicien, et il faut toujours vérifier les étiquettes pour les allergènes.
- [ ] **Licences** : citer Ciqual (Licence Ouverte Etalab 2.0) et garder l'avis SQLCipher dans l'écran Licences. Voir `ASSETS_LICENSES.md`.
- [ ] **IA (optionnelle)** : la clé API personnelle est stockée chiffrée sur le téléphone, jamais dans le code ni dans git. La requête envoyée ne contient ni poids, ni âge, ni identifiant (seulement les allergènes à exclure, les régimes, la cible calorique du repas, le mois et les goûts). Les conditions du fournisseur s'appliquent à ces envois.

## Si l'app devait un jour être partagée ou publiée
Tout redevient applicable : RGPD art. 9 (consentement explicite, AIPD, droits), CGU, mentions légales LCEN, politique de confidentialité, règles Google Play pour les applis de santé, et CGV en cas de monétisation. Le plan initial (version « publication ») est conservé dans l'historique git : commits `34ec33b` à `b9a9f14`.
