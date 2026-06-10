package com.mygame.ui;

import com.mygame.cards.base.Card;
import com.mygame.cards.property.BuildingCard;
import com.mygame.cards.property.PropertyCard;
import com.mygame.core.interaction.GameInteractor;
import com.mygame.model.Color;
import com.mygame.model.PlayerManagement;
import com.mygame.model.PropertyZone;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;
import javafx.stage.WindowEvent;

import java.util.*;
import java.util.stream.Collectors;

public class Interactor implements GameInteractor {

    private Dialog<?> activeDialog;

    public void closeActiveDialogs() {
        if (activeDialog != null) {
            javafx.application.Platform.runLater(() -> {
                if (activeDialog != null) {
                    if (activeDialog instanceof Dialog) {
                        Dialog<?> d = (Dialog<?>) activeDialog;
                        if (d.isShowing()) {
                            // Close via Window so it breaks out of showAndWait properly
                            d.getDialogPane().getScene().getWindow().hide();
                        }
                    }
                }
                activeDialog = null;
            });
        }
    }

    public List<Card> showSelectableAssets(PlayerManagement targetPlayer, int requiredAmount) {
        if (targetPlayer == null) throw new IllegalArgumentException("targetPlayer cannot be null");
        if (requiredAmount < 0) throw new IllegalArgumentException("requiredAmount cannot be negative");

        Dialog<List<Card>> dialog = new Dialog<>();
        activeDialog = dialog;
        dialog.setTitle("Select Assets");
        dialog.setHeaderText("Select assets of " + targetPlayer.getName() + " (multiple selection)");
        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().add(confirmButtonType);

        VBox content = new VBox(10);
        content.setPrefWidth(520);

        Label requiredAmountLabel = new Label("Required amount: " + requiredAmount + "M");
        requiredAmountLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label selectedAmountLabel = new Label("Selected total: 0M");
        Label paymentRuleLabel = new Label(
                "Pay with cards on the table only: bank, property, house, or hotel. "
                        + "Hand cards cannot be used. Confirm when the total meets or exceeds the required amount. No change is given."
        );
        paymentRuleLabel.setWrapText(true);
        paymentRuleLabel.setStyle("-fx-text-fill: #475569;");
        content.getChildren().addAll(requiredAmountLabel, selectedAmountLabel, paymentRuleLabel);

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

        boolean hasAnyAssets = !(bankCheckMap.isEmpty() && propertyCheckMap.isEmpty());

        // Defer button lookup and listener wiring until dialog is shown,
        // so the confirm button is guaranteed to exist.
        dialog.setOnShown(ev -> {
            dialog.getDialogPane().getScene().getWindow().addEventFilter(
                    WindowEvent.WINDOW_CLOSE_REQUEST, e -> e.consume());

            Node confirmButton = dialog.getDialogPane().lookupButton(confirmButtonType);
            if (confirmButton == null) {
                System.err.println("[PaymentDialog] confirmButton is null — cannot enforce payment selection");
                return;
            }

            Runnable refreshSelectionState = () -> {
                int total = 0;
                for (Map.Entry<CheckBox, Card> e : bankCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) total += e.getValue().getValue();
                }
                for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) total += e.getValue().getValue();
                }
                String status = total >= requiredAmount
                        ? "Selected total: " + total + "M (enough to pay)"
                        : "Selected total: " + total + "M (not enough, all assets will be taken)";
                selectedAmountLabel.setText(status);
                confirmButton.setDisable(hasAnyAssets && total == 0);
            };

            for (CheckBox box : bankCheckMap.keySet()) {
                box.selectedProperty().addListener((obs, oldV, newV) -> refreshSelectionState.run());
            }
            for (CheckBox box : propertyCheckMap.keySet()) {
                box.selectedProperty().addListener((obs, oldV, newV) -> refreshSelectionState.run());
            }
            refreshSelectionState.run();
        });

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

    @Override
    public Card choiceProperty(PlayerManagement targetPlayer) {
        if (targetPlayer == null) throw new IllegalArgumentException("targetPlayer cannot be null");

        Dialog<Card> dialog = new Dialog<>();
        activeDialog = dialog;
        dialog.setTitle("Choose property card");
        dialog.setHeaderText("Select one property card from " + targetPlayer.getName());

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, cancelButtonType);

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

    @Override
    public PropertyZone choicePropertyZone(PlayerManagement player) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");

        Dialog<PropertyZone> dialog = new Dialog<>();
        activeDialog = dialog;
        dialog.setTitle("Choose property color");
        dialog.setHeaderText("Select a property set of " + player.getName());

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, cancelButtonType);

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

    @Override
    public PropertyZone choiceBuildingPropertyZone(PlayerManagement player, Card buildingCard) {
        if (player == null) throw new IllegalArgumentException("player cannot be null");
        if (buildingCard == null) throw new IllegalArgumentException("buildingCard cannot be null");

        boolean isHouse = buildingCard.getName() != null && buildingCard.getName().toLowerCase().contains("house");
        boolean isHotel = buildingCard.getName() != null && buildingCard.getName().toLowerCase().contains("hotel");

        Dialog<PropertyZone> dialog = new Dialog<>();
        activeDialog = dialog;
        dialog.setTitle("Choose property set");
        dialog.setHeaderText("Select a complete set for " + buildingCard.getName());

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, cancelButtonType);

        VBox content = new VBox(10);
        content.setPrefWidth(520);
        Map<CheckBox, PropertyZone> zoneCheckMap = new LinkedHashMap<>();

        VBox zoneList = new VBox(6);
        for (Map.Entry<Color, PropertyZone> entry : player.getPropertyZonesView().entrySet()) {
            Color color = entry.getKey();
            PropertyZone zone = entry.getValue();
            int propCount = zone.getPropertiesView().size();
            int requiredSize = player.getRequiredSetSize(color);
            boolean complete = player.isSetComplete(color);

            System.err.println("[BuildingDialog] zone=" + color.name()
                    + " props=" + propCount
                    + " required=" + requiredSize
                    + " complete=" + complete
                    + " hasHouse=" + (zone.getHouse() != null)
                    + " hasHotel=" + (zone.getHotel() != null)
                    + " isHouse=" + isHouse
                    + " isHotel=" + isHotel);

            if (!complete) {
                System.err.println("[BuildingDialog]   SKIP: set not complete (" + propCount + "<" + requiredSize + ")");
                continue;
            }
            if (isHouse && (color == Color.RAILROAD || color == Color.UTILITY || zone.getHouse() != null)) {
                System.err.println("[BuildingDialog]   SKIP: house on " + color.name() + " (RR/UT=" + (color == Color.RAILROAD || color == Color.UTILITY) + " hasHouse=" + (zone.getHouse() != null) + ")");
                continue;
            }
            if (isHotel && (color == Color.RAILROAD || color == Color.UTILITY || zone.getHouse() == null || zone.getHotel() != null)) {
                System.err.println("[BuildingDialog]   SKIP: hotel on " + color.name() + " (RR/UT=" + (color == Color.RAILROAD || color == Color.UTILITY) + " hasHouse=" + (zone.getHouse() != null) + " hasHotel=" + (zone.getHotel() != null) + ")");
                continue;
            }

            String label = "[" + color.name() + "] (properties: " + zone.getPropertiesView().size()
                    + (zone.getHouse() != null ? ", House" : "")
                    + (zone.getHotel() != null ? ", Hotel" : "") + ")";
            CheckBox box = new CheckBox(label);
            zoneCheckMap.put(box, zone);
            zoneList.getChildren().add(box);
        }

        if (zoneCheckMap.isEmpty()) {
            zoneList.getChildren().add(new Label("(No valid complete set for this building)"));
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

    @Override
    public PlayerManagement choiceTargetPlayer(PlayerManagement currentPlayer, List<PlayerManagement> allPlayers) {
        if (currentPlayer == null) throw new IllegalArgumentException("currentPlayer cannot be null");
        if (allPlayers == null) throw new IllegalArgumentException("allPlayers cannot be null");

        List<PlayerManagement> candidates = allPlayers.stream()
                .filter(p -> p != null && p != currentPlayer)
                .collect(Collectors.toList());
        if (candidates.isEmpty()) return null;

        List<String> candidateNames = candidates.stream().map(PlayerManagement::getName).collect(Collectors.toList());
        ChoiceDialog<String> dialog = new ChoiceDialog<>(candidateNames.get(0), candidateNames);
        activeDialog = dialog;
        dialog.setTitle("Choose target player");
        dialog.setHeaderText("Select a target player");
        dialog.setContentText("Target player:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) return null;
        String selectedName = result.get();
        return candidates.stream().filter(p -> p.getName().equals(selectedName)).findFirst().orElse(null);
    }

    @Override
    public boolean confirmJustSayNo(PlayerManagement targetPlayer, PlayerManagement sourcePlayer, String actionName) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        activeDialog = alert;
        alert.setTitle("Just Say No");
        alert.setHeaderText(targetPlayer.getName() + " do you want to play Just Say No?");
        alert.setContentText(sourcePlayer.getName() + " used " + actionName + ". Cancel it?");
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    @Override
    public boolean confirmUseDoubleTheRent(PlayerManagement currentPlayer, Color selectedColor, int baseRentAmount) {
        ButtonType yesButton = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType("No", ButtonBar.ButtonData.NO);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "You have a Double The Rent card.\nDo you want to play it to double the rent to " + (baseRentAmount * 2) + "M?",
                yesButton, noButton);
        activeDialog = alert;
        alert.setTitle("Double The Rent?");
        alert.setHeaderText("Double Rent");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == yesButton;
    }

    @Override
    public Card choiceStealablePropertyCard(PlayerManagement targetPlayer) {
        if (targetPlayer == null) throw new IllegalArgumentException("targetPlayer cannot be null");

        Dialog<Card> dialog = new Dialog<>();
        activeDialog = dialog;
        dialog.setTitle("Choose stealable property");
        dialog.setHeaderText("Select a property card that is not part of a complete set");

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, cancelButtonType);

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
