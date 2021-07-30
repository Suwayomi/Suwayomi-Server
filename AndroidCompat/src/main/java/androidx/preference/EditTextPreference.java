package androidx.preference;

import android.content.Context;

public class EditTextPreference extends Preference {
    private String title;
    private CharSequence summary;
    private CharSequence dialogTitle;
    private CharSequence dialogMessage;

    public EditTextPreference(Context context) {
        super(context);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(CharSequence title) {
        this.title = (String) title;
    }

    public CharSequence getSummary() {
        return summary;
    }

    public void setSummary(CharSequence summary) {
        this.summary = summary;
    }

    public CharSequence getDialogTitle() {
        return dialogTitle;
    }

    public void setDialogTitle(CharSequence dialogTitle) {
        this.dialogTitle = dialogTitle;
    }

    public CharSequence getDialogMessage() {
        return dialogMessage;
    }

    public void setDialogMessage(CharSequence dialogMessage) {
        this.dialogMessage = dialogMessage;
    }
}
