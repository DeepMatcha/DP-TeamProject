import com.holub.life.api.*;

/** Compile-only View: caller reports request failures; listener handles automatic errors. */
public class ViewCompileProbe implements ViewPlugin {
    private ModelPort model;
    private ControllerPort controller;
    private final ModelListener modelListener = board -> {};
    private final HostErrorListener errorListener = error -> {};
    public String id() { return "demo:view"; }
    public void attach(ModelPort model, ControllerPort controller) {
        this.model = model;
        this.controller = controller;
        model.addListener(modelListener);
        controller.addErrorListener(errorListener);
    }
    public void stepFromUser() {
        controller.step().whenComplete((board, error) -> {
            if (error != null) {
                // Dispatch to the UI thread and display the error in a real View.
            }
        });
    }
    public void detach() {
        if (model != null) model.removeListener(modelListener);
        if (controller != null) controller.removeErrorListener(errorListener);
        model = null;
        controller = null;
    }
}
