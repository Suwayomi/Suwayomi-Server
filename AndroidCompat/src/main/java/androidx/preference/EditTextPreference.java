package androidx.preference;

import android.content.Context;

public class EditTextPreference extends Preference {
    // reference: https://android.googlesource.com/platform/frameworks/support/+/996971f962fcd554339a7cb2859cef9ca89dbcb7/preference/preference/src/main/java/androidx/preference/EditTextPreference.java

    private String title;
    private String text;
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

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
