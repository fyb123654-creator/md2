package com.mygame.ui;

import com.mygame.cards.base.Card;
import com.mygame.cards.property.BuildingCard;
import com.mygame.cards.property.PropertyCard;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;

import java.util.*;
import java.util.stream.Collectors;

public class Interactor {

    public List<Card> showSelectableAssets(PlayerManagement targetPlayer, int requiredAmount) {
        if (targetPlayer == null) throw new IllegalArgumentException("targetPlayer cannot be null");
        if (requiredAmount < 0) throw new IllegalArgumentException("requiredAmount cannot be negative");

        Dialog<List<Card>> dialog = new Dialog<>();
        dialog.setTitle("Select Assets");
        dialog.setHeaderText("Select assets of " + targetPlayer.getName() + " (multiple selection)");

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);

        Label requiredAmountLabel = new Label("Required amount: " + requiredAmount + "M");
        requiredAmountLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label selectedAmountLabel = new Label("Selected total: 0M");
        content.getChildren().addAll(requiredAmountLabel, selectedAmountLabel);

        Map<CheckBox, Card> bankCheckMap = new LinkedHashMap<>();
        Map<CheckBox, Card> propertyCheckMap = new LinkedHashMap<>();

        Label bankTitle = new Label("Bank area");
        bankTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        content.getChildren().add(bankTitle);
        VBox bankList = new VBox(6);
        List<Card> bankCards = targetPlayer.getBankCardsView();
        if (bankCards.isEmpty()) {
            bankList.getChildren().add(new Label("(No bank cards)"));
        } else {
            for (Card card : bankCards) {
                CheckBox box = new CheckBox(card.getName() + " (value " + card.getValue() + "M)");
                bankCheckMap.put(box, card);
                bankList.getChildren().add(box);
            }
        }
        content.getChildren().add(bankList);

        Label propertyTitle = new Label("Property area");
        propertyTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        content.getChildren().add(propertyTitle);
        VBox propertyList = new VBox(6);
        Map<Color, PropertyZone> zones = targetPlayer.getPropertyZonesView();
        if (zones.isEmpty()) {
            propertyList.getChildren().add(new Label("(No property cards)"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();
                for (PropertyCard propertyCard : zone.getPropertiesView()) {
                    CheckBox box = new CheckBox("[" + color.name() + "] " + propertyCard.getName() + " (value " + propertyCard.getValue() + "M)");
                    propertyCheckMap.put(box, propertyCard);
                    propertyList.getChildren().add(box);
                }
                if (zone.getHouse() != null) {
                    BuildingCard house = zone.getHouse();
                    CheckBox box = new CheckBox("[" + color.name() + "] House (value " + house.getValue() + "M)");
                    propertyCheckMap.put(box, house);
                    propertyList.getChildren().add(box);
                }
                if (zone.getHotel() != null) {
                    BuildingCard hotel = zone.getHotel();
                    CheckBox box = new CheckBox("[" + color.name() + "] Hotel (value " + hotel.getValue() + "M)");
                    propertyCheckMap.put(box, hotel);
                    propertyList.getChildren().add(box);
                }
            }
        }
        content.getChildren().add(propertyList);

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(420);
        dialog.getDialogPane().setContent(scrollPane);

        Node confirmButton = dialog.getDialogPane().lookupButton(confirmButtonType);
        confirmButton.setDisable(requiredAmount > 0);

        Runnable refreshSelectionState = () -> {
            int total = 0;
            for (Map.Entry<CheckBox, Card> e : bankCheckMap.entrySet()) {
                if (e.getKey().isSelected()) total += e.getValue().getValue();
            }
            for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                if (e.getKey().isSelected()) total += e.getValue().getValue();
            }
            selectedAmountLabel.setText("Selected total: " + total + "M");
            confirmButton.setDisable(total < requiredAmount);
        };

        for (CheckBox box : bankCheckMap.keySet()) {
            box.selectedProperty().addListener((obs, oldV, newV) -> refreshSelectionState.run());
        }
        for (CheckBox box : propertyCheckMap.keySet()) {
            box.selectedProperty().addListener((obs, oldV, newV) -> refreshSelectionState.run());
        }
        refreshSelectionState.run();

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                List<Card> selected = new ArrayList<>();
                for (Map.Entry<CheckBox, Card> e : bankCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) selected.add(e.getValue());
                }
                for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) selected.add(e.getValue());
                }
                return selected;
            }
            return null;
        });

        Optional<List<Card>> result = dialog.showAndWait();
        return result.orElseGet(ArrayList::new);
    }

    public Card choicePorperty(PlayerManagement targetPlayer) {
        if (targetPlayer == null) throw new IllegalArgumentException("targetPlayer cannot be null");

        Dialog<Card> dialog = new Dialog<>();
        dialog.setTitle("Choose property card");
        dialog.setHeaderText("Select one property card from " + targetPlayer.getName());

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);
        Map<CheckBox, Card> propertyCheckMap = new LinkedHashMap<>();

        VBox propertyList = new VBox(6);
        Map<Color, PropertyZone> zones = targetPlayer.getPropertyZonesView();
        if (zones.isEmpty()) {
            propertyList.getChildren().add(new Label("(No property cards)"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();
                for (PropertyCard propertyCard : zone.getPropertiesView()) {
                    CheckBox box = new CheckBox("[" + color.name() + "] " + propertyCard.getName() + " (value " + propertyCard.getValue() + "M)");
                    propertyCheckMap.put(box, propertyCard);
                    propertyList.getChildren().add(box);
                }
                if (zone.getHouse() != null) {
                    BuildingCard house = zone.getHouse();
                    CheckBox box = new CheckBox("[" + color.name() + "] House (value " + house.getValue() + "M)");
                    propertyCheckMap.put(box, house);
                    propertyList.getChildren().add(box);
                }
                if (zone.getHotel() != null) {
                    BuildingCard hotel = zone.getHotel();
                    CheckBox box = new CheckBox("[" + color.name() + "] Hotel (value " + hotel.getValue() + "M)");
                    propertyCheckMap.put(box, hotel);
                    propertyList.getChildren().add(box);
                }
            }
        }
        content.getChildren().add(propertyList);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(360);
        dialog.getDialogPane().setContent(scrollPane);

        Node confirmButton = dialog.getDialogPane().lookupButton(confirmButtonType);
        confirmButton.setDisable(true);

        for (CheckBox currentBox : propertyCheckMap.keySet()) {
            currentBox.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    for (CheckBox otherBox : propertyCheckMap.keySet()) {
                        if (otherBox != currentBox) otherBox.setSelected(false);
                    }
                }
                boolean hasSelection = propertyCheckMap.keySet().stream().anyMatch(CheckBox::isSelected);
                confirmButton.setDisable(!hasSelection);
            });
        }

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) return e.getValue();
                }
            }
            return null;
        });

        Optional<Card> result = dialog.showAndWait();
        return result.orElse(null);
    }

    public PropertyZone choicePropertyZone(PlayerManagement player) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        Dialog<PropertyZone> dialog = new Dialog<>();
        dialog.setTitle("Choose property color");
        dialog.setHeaderText("Select a property set of " + player.getName());

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);
        Map<CheckBox, PropertyZone> zoneCheckMap = new LinkedHashMap<>();

        VBox zoneList = new VBox(6);
        Map<Color, PropertyZone> zones = player.getPropertyZonesView();
        if (zones.isEmpty()) {
            zoneList.getChildren().add(new Label("(No property sets)"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();
                int propertyCount = zone.getPropertiesView().size();
                boolean hasHouse = zone.getHouse() != null;
                boolean hasHotel = zone.getHotel() != null;
                String label = "[" + color.name() + "] (properties: " + propertyCount
                        + (hasHouse ? ", House" : "") + (hasHotel ? ", Hotel" : "") + ")";
                CheckBox box = new CheckBox(label);
                zoneCheckMap.put(box, zone);
                zoneList.getChildren().add(box);
            }
        }
        content.getChildren().add(zoneList);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(360);
        dialog.getDialogPane().setContent(scrollPane);

        Node confirmButton = dialog.getDialogPane().lookupButton(confirmButtonType);
        confirmButton.setDisable(true);

        for (CheckBox currentBox : zoneCheckMap.keySet()) {
            currentBox.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    for (CheckBox otherBox : zoneCheckMap.keySet()) {
                        if (otherBox != currentBox) otherBox.setSelected(false);
                    }
                }
                boolean hasSelection = zoneCheckMap.keySet().stream().anyMatch(CheckBox::isSelected);
                confirmButton.setDisable(!hasSelection);
            });
        }

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                for (Map.Entry<CheckBox, PropertyZone> e : zoneCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) return e.getValue();
                }
            }
            return null;
        });

        Optional<PropertyZone> result = dialog.showAndWait();
        return result.orElse(null);
    }

    public PlayerManagement choiceTargetPlayer(PlayerManagement currentPlayer, List<PlayerManagement> allPlayers) {
        if (currentPlayer == null) throw new IllegalArgumentException("currentPlayer cannot be null");
        if (allPlayers == null) throw new IllegalArgumentException("allPlayers cannot be null");

        List<PlayerManagement> candidates = allPlayers.stream()
                .filter(p -> p != null && p != currentPlayer)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) return null;

        List<String> candidateNames = candidates.stream().map(PlayerManagement::getName).collect(Collectors.toList());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(candidateNames.get(0), candidateNames);
        dialog.setTitle("Choose target player");
        dialog.setHeaderText("Select a target player");
        dialog.setContentText("Target player:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return null;
        String selectedName = result.get();
        return candidates.stream().filter(p -> p.getName().equals(selectedName)).findFirst().orElse(null);
    }

    public boolean confirmJustSayNo(PlayerManagement targetPlayer, PlayerManagement sourcePlayer, String actionName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Just Say No");
        alert.setHeaderText(targetPlayer.getName() + " do you want to play Just Say No?");
        alert.setContentText(sourcePlayer.getName() + " used " + actionName + ". Cancel it?");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public boolean confirmUseDoubleTheRent(PlayerManagement currentPlayer, Color selectedColor, int baseRentAmount) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Double The Rent");
        alert.setHeaderText("Do you want to use Double The Rent?");
        alert.setContentText(currentPlayer.getName() + "'s rent for " + selectedColor.name() + " is " + baseRentAmount + "M. Double it?");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public Card choiceStealablePropertyCard(PlayerManagement targetPlayer) {
        if (targetPlayer == null) throw new IllegalArgumentException("targetPlayer cannot be null");

        Dialog<Card> dialog = new Dialog<>();
        dialog.setTitle("Choose stealable property");
        dialog.setHeaderText("Select a property card that is not part of a complete set");

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);
        Map<CheckBox, Card> propertyCheckMap = new LinkedHashMap<>();

        VBox propertyList = new VBox(6);
        Map<Color, PropertyZone> zones = targetPlayer.getPropertyZonesView();
        if (zones.isEmpty()) {
            propertyList.getChildren().add(new Label("(No stealable properties)"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();
                if (targetPlayer.isSetComplete(color)) continue;
                for (PropertyCard propertyCard : zone.getPropertiesView()) {
                    CheckBox box = new CheckBox("[" + color.name() + "] " + propertyCard.getName() + " (value " + propertyCard.getValue() + "M)");
                    propertyCheckMap.put(box, propertyCard);
                    propertyList.getChildren().add(box);
                }
            }
            if (propertyList.getChildren().isEmpty()) {
                propertyList.getChildren().add(new Label("(No eligible cards)"));
            }
        }
        content.getChildren().add(propertyList);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(360);
        dialog.getDialogPane().setContent(scrollPane);

        Node confirmButton = dialog.getDialogPane().lookupButton(confirmButtonType);
        confirmButton.setDisable(true);

        for (CheckBox currentBox : propertyCheckMap.keySet()) {
            currentBox.selectedProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    for (CheckBox otherBox : propertyCheckMap.keySet()) {
                        if (otherBox != currentBox) otherBox.setSelected(false);
                    }
                }
                boolean hasSelection = propertyCheckMap.keySet().stream().anyMatch(CheckBox::isSelected);
                confirmButton.setDisable(!hasSelection);
            });
        }

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) return e.getValue();
                }
            }
            return null;
        });

        Optional<Card> result = dialog.showAndWait();
        return result.orElse(null);
    }
}
