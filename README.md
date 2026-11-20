# Rivalia

**Rivalia** es una aplicación Android para organizar torneos y competiciones de videojuegos entre amigos y llevar un **ranking ELO permanente** de quién es el mejor del grupo.

Proyecto Intermodular · CFGS Desarrollo de Aplicaciones Multiplataforma (2.º DAM) · Curso 2026-2027 · Rubén Palma

## Funcionalidades

- **Cuentas**: registro e inicio de sesión con correo y contraseña (Firebase Authentication), recuperación de contraseña y control de edad (14+).
- **Grupos (crews)**: crear un grupo, invitar con un código de 6 caracteres, renombrar, salir y cambiar el alias.
- **Partidas rápidas**: se apuntan en segundos, con marcador opcional y empates.
- **Ranking ELO por juego**: cada partida actualiza la puntuación con la fórmula ELO (K = 32, inicio en 1000).
- **Torneos**: eliminatoria directa (con sorteo y *byes* automáticos) o liguilla todos contra todos, con el cuadro en tiempo real.
- **Cara a cara**: historial, balance y rachas contra cada rival.
- **Temporadas y trofeos**: el creador cierra la temporada, el líder de cada juego gana un trofeo y el ranking se reinicia. Los campeones de torneo también reciben trofeo.
- **Estadísticas y logros**: gráfica de evolución del ELO y logros desbloqueables.
- **Sin conexión**: las partidas se guardan en una base de datos SQLite local y se sincronizan solas cuando vuelve la red.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Lenguaje | Kotlin |
| Interfaz | Jetpack Compose + Material 3 |
| Arquitectura | MVVM (ViewModel + StateFlow) y repositorio |
| Autenticación | Firebase Authentication |
| Base de datos remota | Cloud Firestore (tiempo real, región europe-southwest1) |
| Base de datos local | SQLite (`SQLiteOpenHelper`) |
| Asincronía | Corrutinas y Flow |
| Pruebas | JUnit 4 (lógica de ELO, cuadros y estadísticas) |

## Estructura del código

```
app/src/main/java/com/example/rivalia/
├── Models.kt              Modelos de dominio
├── Elo.kt                 Cálculo del ELO
├── Brackets.kt            Generación de cuadros y liguillas
├── Stats.kt               Cara a cara, evolución del ELO y logros
├── LocalDatabase.kt       Base de datos local SQLite
├── FirestoreMappers.kt    Conversión Firestore <-> modelos
├── RivaliaRepository.kt   Repositorio (Firestore + SQLite)
├── AuthRepository.kt      Firebase Authentication
├── SyncManager.kt         Sincronización de partidas sin conexión
├── RivaliaApp.kt          Application e inyección de dependencias
├── ViewModels.kt          ViewModels (MVVM)
├── MainActivity.kt        Navegación
└── *Screen*.kt            Pantallas en Jetpack Compose
firestore.rules            Reglas de seguridad de Cloud Firestore
```

## Modelo de datos en Firestore

```
users/{uid}
crews/{crewId}
    matches/{matchId}
    ratings/{temporada_juego_uid}
    tournaments/{tournamentId}
    trophies/{trophyId}
```

## Cómo ejecutarla

1. Abrir el proyecto con Android Studio (AGP 9.4, Gradle 9.6, JDK 21 o superior).
2. El archivo `app/google-services.json` ya apunta al proyecto de Firebase. Para usar otro proyecto, sustitúyelo por el tuyo y publica `firestore.rules` en Firestore.
3. Ejecutar en un emulador o móvil con Android 7.0 (API 24) o superior.

Los tests unitarios de la lógica se ejecutan con `gradlew test`.
