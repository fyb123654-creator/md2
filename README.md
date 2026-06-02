# Monopoly Deal Card Game

> JavaFX-based digital card game | Software Engineering Project — Group 9

## Overview

A digital implementation of the Monopoly Deal card game built with Java and JavaFX. Supports local multiplayer (2–5 players) and online multiplayer via TCP socket networking. Players collect property sets, charge rent, and use action cards — first to 3 complete sets wins.

GitHub: https://github.com/fyb123654-creator/md2

## Tech Stack

- Java 25 + JavaFX 25 (FXML + CSS)
- Maven (build)
- JUnit 5 (testing)
- Custom TCP protocol (online mode)

## Project Structure

```
md2/
├── pom.xml
├── README.md
├── src/
│   ├── main/java/com/mygame/
│   │   ├── app/              # Entry point & config (GameApp, AppSettings)
│   │   ├── cards/
│   │   │   ├── base/         # Card interfaces (Card, ActionCard, CardType)
│   │   │   ├── action/       # Action cards (SlyDeal, DealBreaker, DebtCollector, etc.)
│   │   │   ├── money/        # Money cards (1M–10M)
│   │   │   ├── property/     # Property cards (standard/wild/house/hotel)
│   │   │   └── rent/         # Rent cards (bi-color/multi-color/wild)
│   │   ├── core/
│   │   │   ├── deck/         # Deck management (106 cards, shuffle/draw/discard)
│   │   │   ├── events/       # Event system (GameEvent, listener)
│   │   │   └── interaction/  # Interaction interface (GameInteractor)
│   │   ├── model/            # Domain model (Player, PropertyZone, Color)
│   │   ├── ui/
│   │   │   └── components/   # Card UI component (CardView)
│   │   ├── network/          # Multiplayer (GameServer, GameClient, DTO, protocol)
│   │   └── rules/            # Rent calculation rules
│   └── test/java/com/mygame/
│       ├── core/             # Core engine tests
│       └── cards/action/     # Action card tests
└── docs/
```

## Quick Start

```bash
# Build & run (requires JDK 25 + Maven)
mvnw javafx:run

# Run tests
mvnw test
```

## Game Rules

**Goal:** Be the first player to collect 3 complete property sets.

**Each turn:**
1. Draw 2 cards (automatic)
2. Play up to 3 cards (deposit to bank / place property / play action)
3. End Turn (discard down to 7 hand cards if needed)

## Card Types

| Type | Description |
|------|-------------|
| Money | 1M–10M face value, deposited to bank |
| Property | Placed in a color zone; collect rent when set is complete |
| Action | One-shot effects — SlyDeal, DealBreaker, DebtCollector, etc. |
| Building | House (+3M rent) / Hotel (+5M rent) on a complete set |
| Rent | Collect rent from opponent(s); can stack with DoubleTheRent |

## Features

- Local multiplayer (2–5 players)
- Online multiplayer via TCP sockets
- Full 106-card standard deck
- JustSayNo counter-chain
- House/Hotel building system
- In-game chat + event log
- Card hover preview with magnification
- Customizable player names and avatars
- CSS theming

## Tests

26 test cases across 6 test classes:

| Test Class | Cases | Coverage |
|-----------|-------|----------|
| GameManagerTest | 5 | Init, bank deposit, property placement, turn advance, steal-to-win |
| CardManagerTest | 1 | Draw pile reshuffle from discard |
| ActionCardTest | 8 | SlyDeal steal & JustSayNo cancel, DealBreaker set theft, DebtCollector charge, PassGo draw, It'sMyBirthday mass charge |
| PlayerManagementTest | 5 | Asset valuation, asset transfer, set completion, rent with buildings |
| GameStateDataTest | 4 | Card serialization round-trip, player data, game state DTO |
| PropertyRentRulesTest | 3 | Rent values for brown, dark blue, and railroad sets |
