# TABS - A News Reading App

**CSE 535: Mobile Computing Project**

**Team:** Ananya Garg, Ishika Gupta, Swarnima Prasad

---

## Overview

Tabs is a modern Android news reading application designed for easy access to categorized news articles fetched via the NewsAPI. It emphasizes a smooth user experience with offline caching (via Room DB), robust error handling, network awareness, and key accessibility features.

---

## Key Features

*   **News Browsing & Reading:** Fetches and displays breaking news and category-specific articles. Allows viewing full article details.
*   **Categorization & Filtering:** Users can filter news by selecting categories (e.g., Business, Technology, Sports) using Material Chips.
*   **Search:**
    *   Text-based search using the toolbar `SearchView`.
    *   Voice search initiated via a dedicated toolbar icon, utilizing the Android `SpeechRecognizer`.
*   **Offline Access:** Users can save articles for later reading, accessible even when offline.
*   **Saved News Management:** View saved articles and delete all saved items.
*   **Theme Customization:** Supports both Light and Dark modes, with toggles available for user preference. The chosen theme persists across app sessions.
*   **Accessibility:** Compatible with Android's TalkBack service for audio feedback.
*   **Network Awareness:** Checks for network connectivity before fetching data and displays appropriate UI state (content or no-connection message).

---

## Architecture

Built using the **MVVM (Model-View-ViewModel)** architecture pattern to ensure a clear separation of concerns:
*   **View (UI Layer):** Activities (using Edge-to-Edge) & Fragments displaying data via `RecyclerView` and handling user input. Uses Navigation Component.
*   **ViewModel (Logic Layer):** Manages UI-related data using `LiveData`, interacts with the Repository, handles business logic, and survives configuration changes.
*   **Model (Data Layer):** `Repository` pattern managing data sources (Network via Retrofit, Local DB via Room).

---

## Tech Stack

*   **Language:** Kotlin
*   **Architecture:** MVVM, Hilt (Dependency Injection)
*   **UI:** XML Layouts, Material Components/Material 3, Navigation Component, View Binding
*   **Asynchronous Programming:** Kotlin Coroutines
*   **Networking:** Retrofit, OkHttp, Gson
*   **Database:** Room Persistence Library
*   **Image Loading:** Glide
*   **Other Key Features:** Android SpeechRecognizer, Core Splashscreen API, AppCompat DayNight Theming

--- ⁠
