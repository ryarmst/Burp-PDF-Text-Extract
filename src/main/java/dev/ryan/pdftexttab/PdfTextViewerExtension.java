package dev.ryan.pdftexttab;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.UserInterface;
import burp.api.montoya.ui.editor.extension.HttpResponseEditorProvider;

public class PdfTextViewerExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        Logging log = api.logging();
        UserInterface ui = api.userInterface();

        log.logToOutput("Loading PDF Text viewer tab…");
        HttpResponseEditorProvider provider = new PdfTextViewerProvider(api);
        ui.registerHttpResponseEditorProvider(provider);
        log.logToOutput("PDF Text viewer tab registered.");
    }
}

