package com.mygame;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class Interactor {

    /**
     * 展示目标玩家可被选择的资产（银行区 + 物业区），支持多选。
     * 只有当已选卡牌总面值 >= 应付金额时，才能点击确认。
     *
     * @param targetPlayer 目标玩家
     * @param requiredAmount 应付金额（M）
     * @return 玩家确认后选中的卡牌；若取消则返回空列表
     */
    public List<Card> showSelectableAssets(PlayerManagement targetPlayer, int requiredAmount) {
        if (targetPlayer == null) {
            throw new IllegalArgumentException("targetPlayer cannot be null");
        }
        if (requiredAmount < 0) {
            throw new IllegalArgumentException("requiredAmount cannot be negative");
        }

        Dialog<List<Card>> dialog = new Dialog<>();
        dialog.setTitle("选择资产");
        dialog.setHeaderText("请选择 " + targetPlayer.getName() + " 的资产（可多选）");

        ButtonType confirmButtonType = new ButtonType("确认", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);

        Label requiredAmountLabel = new Label("应付金额: " + requiredAmount + "M");
        requiredAmountLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        Label selectedAmountLabel = new Label("已选总额: 0M");
        content.getChildren().addAll(requiredAmountLabel, selectedAmountLabel);

        List<Card> selectedCards = new ArrayList<>();
        Map<CheckBox, Card> bankCheckMap = new LinkedHashMap<>();
        Map<CheckBox, Card> propertyCheckMap = new LinkedHashMap<>();

        Label bankTitle = new Label("银行区");
        bankTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        content.getChildren().add(bankTitle);

        VBox bankList = new VBox(6);
        List<Card> bankCards = targetPlayer.getBankCardsView();
        if (bankCards.isEmpty()) {
            bankList.getChildren().add(new Label("（无银行卡牌）"));
        } else {
            for (Card card : bankCards) {
                CheckBox box = new CheckBox(card.getName() + "（价值 " + card.getValue() + "M）");
                bankCheckMap.put(box, card);
                bankList.getChildren().add(box);
            }
        }
        content.getChildren().add(bankList);

        Label propertyTitle = new Label("物业区");
        propertyTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        content.getChildren().add(propertyTitle);

        VBox propertyList = new VBox(6);
        Map<Color, PropertyZone> zones = targetPlayer.getPropertyZonesView();
        if (zones.isEmpty()) {
            propertyList.getChildren().add(new Label("（无物业卡牌）"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();

                for (PropertyCard propertyCard : zone.getPropertiesView()) {
                    CheckBox box = new CheckBox(
                            "[" + color.name() + "] " + propertyCard.getName() + "（价值 " + propertyCard.getValue() + "M）"
                    );
                    propertyCheckMap.put(box, propertyCard);
                    propertyList.getChildren().add(box);
                }

                if (zone.getHouse() != null) {
                    BuildingCard house = zone.getHouse();
                    CheckBox box = new CheckBox(
                            "[" + color.name() + "] House（价值 " + house.getValue() + "M）"
                    );
                    propertyCheckMap.put(box, house);
                    propertyList.getChildren().add(box);
                }

                if (zone.getHotel() != null) {
                    BuildingCard hotel = zone.getHotel();
                    CheckBox box = new CheckBox(
                            "[" + color.name() + "] Hotel（价值 " + hotel.getValue() + "M）"
                    );
                    propertyCheckMap.put(box, hotel);
                    propertyList.getChildren().add(box);
                }
            }

            if (propertyList.getChildren().isEmpty()) {
                propertyList.getChildren().add(new Label("（无物业卡牌）"));
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
                if (e.getKey().isSelected()) {
                    total += e.getValue().getValue();
                }
            }
            for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                if (e.getKey().isSelected()) {
                    total += e.getValue().getValue();
                }
            }

            selectedAmountLabel.setText("已选总额: " + total + "M");
            boolean enough = total >= requiredAmount;
            confirmButton.setDisable(!enough);
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
                selectedCards.clear();

                for (Map.Entry<CheckBox, Card> e : bankCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) {
                        selectedCards.add(e.getValue());
                    }
                }

                for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) {
                        selectedCards.add(e.getValue());
                    }
                }

                return new ArrayList<>(selectedCards);
            }
            return new ArrayList<>();
        });

        Optional<List<Card>> result = dialog.showAndWait();
        return result.orElseGet(ArrayList::new);
    }

    /**
     * 展示目标玩家物业区所有卡牌，并选择其中一张返回。
     *
     * @param targetPlayer 目标玩家
     * @return 选中的卡牌；若取消或无可选卡牌则返回 null
     */
    public Card choicePorperty(PlayerManagement targetPlayer) {
        if (targetPlayer == null) {
            throw new IllegalArgumentException("targetPlayer cannot be null");
        }

        Dialog<Card> dialog = new Dialog<>();
        dialog.setTitle("选择物业卡牌");
        dialog.setHeaderText("请选择 " + targetPlayer.getName() + " 物业区中的一张卡牌");

        ButtonType confirmButtonType = new ButtonType("确认", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);

        Map<CheckBox, Card> propertyCheckMap = new LinkedHashMap<>();

        VBox propertyList = new VBox(6);
        Map<Color, PropertyZone> zones = targetPlayer.getPropertyZonesView();
        if (zones.isEmpty()) {
            propertyList.getChildren().add(new Label("（无物业卡牌）"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();

                for (PropertyCard propertyCard : zone.getPropertiesView()) {
                    CheckBox box = new CheckBox(
                            "[" + color.name() + "] " + propertyCard.getName() + "（价值 " + propertyCard.getValue() + "M）"
                    );
                    propertyCheckMap.put(box, propertyCard);
                    propertyList.getChildren().add(box);
                }

                if (zone.getHouse() != null) {
                    BuildingCard house = zone.getHouse();
                    CheckBox box = new CheckBox(
                            "[" + color.name() + "] House（价值 " + house.getValue() + "M）"
                    );
                    propertyCheckMap.put(box, house);
                    propertyList.getChildren().add(box);
                }

                if (zone.getHotel() != null) {
                    BuildingCard hotel = zone.getHotel();
                    CheckBox box = new CheckBox(
                            "[" + color.name() + "] Hotel（价值 " + hotel.getValue() + "M）"
                    );
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
                        if (otherBox != currentBox) {
                            otherBox.setSelected(false);
                        }
                    }
                }

                boolean hasSelection = false;
                for (CheckBox box : propertyCheckMap.keySet()) {
                    if (box.isSelected()) {
                        hasSelection = true;
                        break;
                    }
                }
                confirmButton.setDisable(!hasSelection);
            });
        }

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) {
                        return e.getValue();
                    }
                }
            }
            return null;
        });

        Optional<Card> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /**
     * 让玩家在自己的物业区中选择一个颜色分组（PropertyZone）。
     *
     * @param player 使用者玩家
     * @return 选中的 PropertyZone；若取消或无可选物业区则返回 null
     */
    public PropertyZone choicePropertyZone(PlayerManagement player) {
        if (player == null) {
            throw new IllegalArgumentException("player cannot be null");
        }

        Dialog<PropertyZone> dialog = new Dialog<>();
        dialog.setTitle("选择物业颜色");
        dialog.setHeaderText("请选择 " + player.getName() + " 的一套物业（点击物业区）");

        ButtonType confirmButtonType = new ButtonType("确认", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);

        Map<CheckBox, PropertyZone> zoneCheckMap = new LinkedHashMap<>();
        Map<Color, PropertyZone> zones = player.getPropertyZonesView();

        VBox zoneList = new VBox(6);
        if (zones.isEmpty()) {
            zoneList.getChildren().add(new Label("（无物业区）"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();

                int propertyCount = zone.getPropertiesView().size();
                boolean hasHouse = zone.getHouse() != null;
                boolean hasHotel = zone.getHotel() != null;

                String label = "[" + color.name() + "] 物业 "
                        + "（地产:" + propertyCount
                        + (hasHouse ? ", House" : "")
                        + (hasHotel ? ", Hotel" : "")
                        + "）";

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
                        if (otherBox != currentBox) {
                            otherBox.setSelected(false);
                        }
                    }
                }

                boolean hasSelection = false;
                for (CheckBox box : zoneCheckMap.keySet()) {
                    if (box.isSelected()) {
                        hasSelection = true;
                        break;
                    }
                }
                confirmButton.setDisable(!hasSelection);
            });
        }

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                for (Map.Entry<CheckBox, PropertyZone> e : zoneCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) {
                        return e.getValue();
                    }
                }
            }
            return null;
        });

        Optional<PropertyZone> result = dialog.showAndWait();
        return result.orElse(null);
    }

    /**
     * 展示除当前玩家外的所有玩家，并让当前玩家选择一个目标玩家。
     *
     * @param currentPlayer 当前玩家
     * @param allPlayers 所有玩家列表
     * @return 选中的目标玩家；若取消或无可选目标则返回 null
     */
    public PlayerManagement choiceTargetPlayer(PlayerManagement currentPlayer, List<PlayerManagement> allPlayers) {
        if (currentPlayer == null) {
            throw new IllegalArgumentException("currentPlayer cannot be null");
        }
        if (allPlayers == null) {
            throw new IllegalArgumentException("allPlayers cannot be null");
        }

        List<PlayerManagement> candidates = allPlayers.stream()
                .filter(player -> player != null && player != currentPlayer)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return null;
        }

        List<String> candidateNames = candidates.stream()
                .map(PlayerManagement::getName)
                .collect(Collectors.toList());

        ChoiceDialog<String> dialog = new ChoiceDialog<>(candidateNames.get(0), candidateNames);
        dialog.setTitle("选择目标玩家");
        dialog.setHeaderText("请选择一个目标玩家");
        dialog.setContentText("目标玩家:");

        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return null;
        }

        String selectedName = result.get();
        for (PlayerManagement player : candidates) {
            if (player.getName().equals(selectedName)) {
                return player;
            }
        }
        return null;
    }

    public boolean confirmJustSayNo(PlayerManagement targetPlayer, PlayerManagement sourcePlayer, String actionName) {
        if (targetPlayer == null) {
            throw new IllegalArgumentException("targetPlayer cannot be null");
        }
        if (sourcePlayer == null) {
            throw new IllegalArgumentException("sourcePlayer cannot be null");
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Just Say No");
        alert.setHeaderText(targetPlayer.getName() + " 是否要打出 Just Say No？");
        alert.setContentText("" + sourcePlayer.getName() + " 使用了 " + actionName + "，是否取消这次效果？");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public boolean confirmUseDoubleTheRent(PlayerManagement currentPlayer, Color selectedColor, int baseRentAmount) {
        if (currentPlayer == null) {
            throw new IllegalArgumentException("currentPlayer cannot be null");
        }
        if (selectedColor == null) {
            throw new IllegalArgumentException("selectedColor cannot be null");
        }
        if (baseRentAmount < 0) {
            throw new IllegalArgumentException("baseRentAmount cannot be negative");
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Double The Rent");
        alert.setHeaderText("是否打出 Double The Rent？");
        alert.setContentText(currentPlayer.getName() + " 的 " + selectedColor.name() + " 租金为 " + baseRentAmount + "M，是否翻倍？");

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    /**
     * 让当前玩家从目标玩家的物业区中选择一张可被 Sly Deal 夺取的卡牌。
     * 只能选择未完成整套的物业颜色组中的普通物业卡，不能选择 House / Hotel。
     *
     * @param targetPlayer 目标玩家
     * @return 选中的可偷取物业卡；若取消或无可选卡牌则返回 null
     */
    public Card choiceStealablePropertyCard(PlayerManagement targetPlayer) {
        if (targetPlayer == null) {
            throw new IllegalArgumentException("targetPlayer cannot be null");
        }

        Dialog<Card> dialog = new Dialog<>();
        dialog.setTitle("选择可偷取的物业卡");
        dialog.setHeaderText("请选择一张未凑齐整套的物业卡");

        ButtonType confirmButtonType = new ButtonType("确认", ButtonType.OK.getButtonData());
        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(520);

        Map<CheckBox, Card> propertyCheckMap = new LinkedHashMap<>();

        VBox propertyList = new VBox(6);
        Map<Color, PropertyZone> zones = targetPlayer.getPropertyZonesView();
        if (zones.isEmpty()) {
            propertyList.getChildren().add(new Label("（无可偷取物业卡）"));
        } else {
            for (Map.Entry<Color, PropertyZone> entry : zones.entrySet()) {
                Color color = entry.getKey();
                PropertyZone zone = entry.getValue();

                if (targetPlayer.isSetComplete(color)) {
                    continue;
                }

                for (PropertyCard propertyCard : zone.getPropertiesView()) {
                    CheckBox box = new CheckBox(
                            "[" + color.name() + "] " + propertyCard.getName() + "（价值 " + propertyCard.getValue() + "M）"
                    );
                    propertyCheckMap.put(box, propertyCard);
                    propertyList.getChildren().add(box);
                }
            }

            if (propertyList.getChildren().isEmpty()) {
                propertyList.getChildren().add(new Label("（没有符合条件的物业卡）"));
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
                        if (otherBox != currentBox) {
                            otherBox.setSelected(false);
                        }
                    }
                }

                boolean hasSelection = false;
                for (CheckBox box : propertyCheckMap.keySet()) {
                    if (box.isSelected()) {
                        hasSelection = true;
                        break;
                    }
                }
                confirmButton.setDisable(!hasSelection);
            });
        }

        dialog.setResultConverter(buttonType -> {
            if (buttonType == confirmButtonType) {
                for (Map.Entry<CheckBox, Card> e : propertyCheckMap.entrySet()) {
                    if (e.getKey().isSelected()) {
                        return e.getValue();
                    }
                }
            }
            return null;
        });

        Optional<Card> result = dialog.showAndWait();
        return result.orElse(null);
    }

}
