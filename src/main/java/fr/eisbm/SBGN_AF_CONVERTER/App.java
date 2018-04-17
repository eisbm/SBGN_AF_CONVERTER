package fr.eisbm.SBGN_AF_CONVERTER;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Component;
import java.io.*;

public class App extends Application {
	

	/**
	 * This enum describes the 2 possible directions of convertion.
	 */
	public enum ConvertionChoice {
		GRAPHML2SBGN, SBGN2GRAPHML;

		@Override
		public String toString() {
			switch (this) {
			case GRAPHML2SBGN:
				return "GraphML -> SBGN-ML";
			case SBGN2GRAPHML:
				return "SBGN-ML -> GraphML";
			}
			throw new IllegalArgumentException("No valid enum was given");
		}
	}
	
	String szFolderName = "";

	@SuppressWarnings({ "restriction", "rawtypes" })
	@Override
	public void start(Stage primaryStage) {

		primaryStage.setTitle("GraphML <-> SBGN-ML");

		VBox vbox = new VBox(10);
		vbox.setPadding(new Insets(10, 10, 10, 10));

		GridPane grid = new GridPane();
		grid.setAlignment(Pos.CENTER);
		grid.setHgap(10);
		grid.setVgap(10);
		vbox.getChildren().add(grid);

		// --- 0th row --- //
		Label directionLabel = new Label("Convertion Direction:");
		grid.add(directionLabel, 0, 0);

		ChoiceBox directionChoice = new ChoiceBox<>(FXCollections.observableArrayList(
				ConvertionChoice.GRAPHML2SBGN.toString(), ConvertionChoice.SBGN2GRAPHML.toString()));
		grid.add(directionChoice, 1, 0);
		directionChoice.getSelectionModel().selectFirst(); // set first as default

		// --- 1st row --- //
		Label inputFileLabel = new Label("Input File:");
		grid.add(inputFileLabel, 0, 1);

		TextField inputFileText = new TextField();
		grid.add(inputFileText, 1, 1);

		FileChooser inputFileChooser = new FileChooser();

		Button inputFileOpenButton = new Button("Choose file");
		
		grid.add(inputFileOpenButton, 2, 1);
		
		inputFileOpenButton.setOnAction(e -> {
			File file = inputFileChooser.showOpenDialog(primaryStage);
			if (file != null) {
				inputFileText.setText(file.getAbsolutePath());
				szFolderName = file.getParentFile().getAbsolutePath();
			}
			
		});

		// --- final row --- //
		final Label infoLabel = new Label();
		Button convertButton = new Button("Convert");
		grid.add(convertButton, 1, 4, 3, 1);
		convertButton.setOnAction(e -> {

			// check arguments
			if (inputFileText.getText().isEmpty()) {
				infoLabel.setText("No input provided.");
				return;
			}

			if (directionChoice.getValue().equals(ConvertionChoice.GRAPHML2SBGN.toString())) {
				System.out.println("Convert button clicked, launch script");
				Task task = new Task<Void>() {
					@Override
					public Void call() {
						Platform.runLater(() -> {
							infoLabel.setText("Running...");
						});
						GraphML2AF.convert(inputFileText.getText());
						
						
						Platform.runLater(() -> {
							infoLabel.setText("Done");
							Alert alert = new Alert(Alert.AlertType.INFORMATION);
							alert.setTitle("The output folder");
							alert.setHeaderText("The output is available in the following folder: ");
							alert.setContentText(szFolderName);
							alert.show();
						});
						return null;
					}
				};
				new Thread(task).start();

			} else if (directionChoice.getValue().equals(ConvertionChoice.SBGN2GRAPHML.toString())) {
				System.out.println("Convert button clicked, launch script");
				Task task = new Task<Void>() {
					@Override
					public Void call() {
						Platform.runLater(() -> {
							infoLabel.setText("Running...");
						});
						SBGNML2GraphML.convert(inputFileText.getText());
						
						Platform.runLater(() -> {
							infoLabel.setText("Done");
						});
						return null;
					}
				};
				new Thread(task).start();

			} else {
				throw new RuntimeException("That shouldn't happen.");
			}

		});

		Button closeButton = new Button("Close");
		grid.add(closeButton, 2, 4, 3, 1);

		closeButton.setOnAction(e -> {

			System.exit(0);

		});

		// info row
		grid.add(infoLabel, 1, 5);

		Scene scene = new Scene(vbox, 800, 400);
		primaryStage.setScene(scene);

		primaryStage.show();
	}

	public class TextOutputStream extends OutputStream {
		TextArea textArea;

		public TextOutputStream(TextArea textArea) {
			this.textArea = textArea;
		}

		@Override
		public void write(int b) throws IOException {
			// redirects data to the text area
			textArea.appendText(String.valueOf((char) b));
			// scrolls the text area to the end of data
			textArea.positionCaret(textArea.getText().length());
		}
	}
}
