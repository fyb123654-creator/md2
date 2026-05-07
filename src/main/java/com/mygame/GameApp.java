package com.mygame;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GameApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. 加载 FXML 布局文件
        // 注意路径：如果 FXML 放在 resources 根目录，前面要加 "/"
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/GameView.fxml"));
        Parent root = loader.load();

        // 2. 获取 Controller 实例，并初始化游戏 (假设 3 个玩家)
        GameController controller = loader.getController();
        controller.initializeGame(3);

        // 3. 设置窗口并显示
        Scene scene = new Scene(root, 1280, 720);
        primaryStage.setTitle("卡牌游戏");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
