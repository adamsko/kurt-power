package com.example.KurtPower;

import android.content.Context;
import android.widget.Chronometer;

public class KurtChronometer extends Chronometer
{
    public KurtChronometer(Context context)
    {
        super(context);
    }

    @Override
    public void onWindowVisibilityChanged(int visibility)
    {
        super.onWindowVisibilityChanged(visibility);
    }
}
