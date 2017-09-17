package de.bluenodes.bluenodescontroller.utility;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.widget.NumberPicker;

import de.bluenodes.bluenodescontroller.R;

/**
 * Created by User on 23.03.2016.
 */
public class ThemedNumberPicker extends NumberPicker {

    public ThemedNumberPicker(Context context) {
        this(context, null);
    }

    public ThemedNumberPicker(Context context, AttributeSet attrs) {
        // wrap the current context in the style we defined before
        super(new ContextThemeWrapper(context, R.style.NumberPickerTextColorStyle), attrs);
    }
}
