# Monopoly Deal Card Game

> JavaFX-based digital card game | Software Engineering Project — Group 9

## Overview

A digital Monopoly Deal project built with Java and JavaFX. The game currently supports:

- Local multiplayer for 2 to 5 players
- Online multiplayer through a host-client TCP room flow
- Turn timer, automatic timeout handling, event log, in-game chat, and image-based UI customization

The win condition is to complete 3 full property sets before the other players.

## Tech Stack

- Java 25
- JavaFX 25 (`controls`, `fxml`, `graphics`, `media`)
- Maven
- JUnit 5
- Custom TCP networking

## Project Structure

```text
mdmdmd/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/mygame/
    │   │   ├── app/            # Entry point, settings, menu/lobby flow
    │   │   ├── cards/          # Card definitions and action logic
    │   │   ├── core/           # Game manager, deck, events, interactor
    │   │   ├── model/          # Player, property zone, colors, assets
    │   │   ├── network/        # Server, client, DTO, protocol
    │   │   ├── rules/          # Rent rules
    │   │   └── ui/             # Controllers and UI components
    │   └── resources/
    │       ├── audio/          # Optional background music slot
    │       ├── images/         # Backgrounds, panels, cards, avatars
    │       ├── GameView.fxml
    │       ├── NetworkGameView.fxml
    │       └── theme.css
    └── test/java/com/mygame/
```

## Quick Start

```bash
# Run the game
mvn javafx:run

# Run tests
mvn test
```

## Core Rules

**Goal**

- Collect 3 complete property sets first.

**Each turn**

1. Draw 2 cards automatically
2. Play up to 3 cards
3. End the turn with 7 or fewer cards in hand

**Turn timer**

- Each turn uses a 180-second countdown
- If time reaches 0, the turn ends automatically
- If the player holds more than 7 cards, extra cards are discarded automatically before the turn closes

## Features

- Offline multiplayer for 2 to 5 players
- Online room system with host create / join / ready / start flow
- In-game chat and match event log
- Custom player name and avatar selection
- Card hover preview and draw/play transition animation
- Visible draw pile and discard pile with custom back art
- Central table area showing current player `Bank / Property / Action`
- Expandable bottom hand drawer
- Image slots for menu, lobby, table, panels, chat, log, buttons, cards, and avatars
- Optional BGM with in-game volume slider
- Auto timeout handling with warning state and auto-end turn behavior

## Resource Slots

Replace assets using these exact resource paths:

- `src/main/resources/images/background.png`
- `src/main/resources/images/menu/menu-hero.png`
- `src/main/resources/images/menu/menu-panel-art.png`
- `src/main/resources/images/lobby/lobby-hero.png`
- `src/main/resources/images/lobby/lobby-panel.png`
- `src/main/resources/images/lobby/lobby-side-art.png`
- `src/main/resources/images/ui/table-surface.png`
- `src/main/resources/images/ui/log-panel.png`
- `src/main/resources/images/ui/chat-box.png`
- `src/main/resources/images/ui/action-panel.png`
- `src/main/resources/images/ui/hand-surface.png`
- `src/main/resources/images/ui/button-overlay.png`
- `src/main/resources/images/cards/card-front-overlay.png`
- `src/main/resources/images/cards/card-back.png`
- `src/main/resources/audio/bgm.mp3`
- `src/main/resources/audio/bgm.wav`

## Tests

28 test cases across 6 test classes:

| Test Class | Cases | Coverage |
|-----------|-------|----------|
| GameManagerTest | 7 | Init, bank deposit, property placement, turn advance, draw rules, steal-to-win, player elimination |
| CardManagerTest | 1 | Draw pile reshuffle from discard pile |
| ActionCardTest | 8 | SlyDeal, DealBreaker, DebtCollector, Pass Go, It's My Birthday, Just Say No interactions |
| PlayerManagementTest | 5 | Asset valuation, asset transfer, set completion, rent with buildings |
| GameStateDataTest | 4 | Card serialization round-trip, player data, game state DTO |
| PropertyRentRulesTest | 3 | Rent values for brown, dark blue, and railroad sets |
