package com.dlsc.fxmlkit.samples.tier1.viewpath;


import com.dlsc.fxmlkit.annotations.FxmlPath;
import com.dlsc.fxmlkit.fxml.FxmlView;

@FxmlPath("my-custom-view.fxml")
public class CustomPathView extends FxmlView<CustomPathController> {
}

//--- Alternative using a ViewProvider ---

// @FxmlPath("my-custom-view.fxml")
// public class CustomPathViewProvider extends FxmlViewProvider<CustomPathController> {
// }
