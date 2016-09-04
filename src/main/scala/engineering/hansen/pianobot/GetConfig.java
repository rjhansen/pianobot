package engineering.hansen.pianobot;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.StageStyle;
import javafx.fxml.Initializable;
import java.net.URL;
import java.util.ResourceBundle;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.io.File;

public class GetConfig extends Application implements Initializable {

    private final Logger logger = LogManager.getLogger(GetConfig.class);
    private boolean shouldSave = false;

    @FXML
    public Button cancel;

    @FXML
    public Button ok;

    @FXML
    public ComboBox<String> cbIRC;

    @FXML
    public ComboBox<String> cbChan;

    @FXML
    public TextField botNick;

    @FXML
    public TextField adminNick;

    @FXML
    public TextField mp3dir;

    @FXML
    public Label helpText;

    @FXML
    public TextField botPassword;

    private final Pattern nickRegex = Pattern.compile("^[A-Za-z0-9_]+$");


    private void validateForm() {
        String _adminNick = adminNick.getText();
        String _botNick = botNick.getText();
        String _mp3dir = mp3dir.getText();
        String _botPassword = botPassword.getText();

        boolean goodAdminNick = nickRegex.matcher(_adminNick).matches();
        boolean goodBotNick = nickRegex.matcher(_botNick).matches();
        boolean goodBotPW = nickRegex.matcher(_botPassword).matches();
        boolean goodMP3Path = false;

        try {
            goodMP3Path = (_mp3dir.length() > 0) && (Files.isDirectory(Paths.get(_mp3dir)));
        } catch (Exception e) {
            // pass
        }

        adminNick.setStyle("-fx-text-fill: " + (goodAdminNick ? "black" : "red") + ";");
        botNick.setStyle("-fx-text-fill: " + (goodBotNick ? "black" : "red") + ";");
        botPassword.setStyle("-fx-text-fill: " + (goodBotPW ? "black" : "red") + ";");
        mp3dir.setStyle("-fx-text-fill: " + (goodMP3Path ? "black" : "red") + ";");

        if (! goodAdminNick)
            helpText.setText("Enter the admin's IRC nick");
        else if (! goodBotNick)
            helpText.setText("Enter the bot's IRC nick");
        else if (! goodBotPW)
            helpText.setText("Enter the bot's IRC password");
        else if (! goodMP3Path)
            helpText.setText("Enter a path to an MP3 library");
        else
            helpText.setText("Click 'Save' to save this configuration.");
        ok.setDisable( ! (goodAdminNick && goodBotNick && goodBotPW && goodMP3Path));
    }

    private void runOnExit() {
        if (! shouldSave) {
            logger.fatal("User canceled out of setup");
            System.exit(-1);
        }
        try {
            String fn = System.getProperty("user.home") + File.separator + ".pianobot"
                    + File.separator + "pianobot.conf";
            PrintWriter pw = new PrintWriter(new FileOutputStream(fn));
            pw.println("# This is an automatically generated configuration file.");
            pw.println("# If you delete this file, the GUI configuration tool will");
            pw.println("# run again on the next launch.");
            pw.println();
            pw.println("admin       = " + adminNick.getText());
            pw.println("bot         = " + botNick.getText());
            pw.println("password    = " + botPassword.getText());
            pw.println("IRC server  = " + cbIRC.getValue());
            pw.println("IRC channel = " + cbChan.getValue());
            pw.close();
        }
        catch (Exception e) {
            logger.fatal(e);
            System.exit(-1);
        }
    }

    public void initialize(URL fxmlFileLocation, ResourceBundle resources) {
        cancel.setOnAction((event) -> { shouldSave = false; runOnExit(); javafx.application.Platform.exit(); });
        ok.setOnAction((event) -> { shouldSave = true; runOnExit(); javafx.application.Platform.exit(); });
        cbIRC.getItems().addAll("irc.freenode.net");
        cbChan.getItems().addAll("#callahans");
        cbIRC.getSelectionModel().select(0);
        cbChan.getSelectionModel().select(0);
        botNick.setOnKeyTyped((event) -> validateForm());
        adminNick.setOnKeyTyped((event) -> validateForm());
        mp3dir.setOnKeyTyped((event) -> validateForm());
    }

    public void runme() {
        javafx.application.Application.launch(getClass());
    }

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/Configuration.fxml"));
        final Stage dialog = new Stage();

        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.initOwner(stage);

        Scene scene = new Scene(root);
        dialog.setTitle("Pianobot Configuration");
        dialog.setScene(scene);
        dialog.show();
        dialog.setResizable(false);
    }
}
