package __PKG__;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * __APP__ — пустой шаблон: один экран с текстом.
 * Отсюда ИИ может достроить что угодно.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(18, 8, 31));

        TextView hello = new TextView(this);
        hello.setText("__APP__");
        hello.setTextColor(Color.rgb(192, 132, 252));
        hello.setTextSize(24);
        hello.setGravity(Gravity.CENTER);

        root.addView(hello);
        setContentView(root);
    }
}
