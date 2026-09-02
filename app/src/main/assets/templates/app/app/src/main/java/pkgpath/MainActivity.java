package __PKG__;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * __APP__ — простой кликер-счётчик.
 * UI собирается кодом, без XML-макетов: меньше файлов — меньше ошибок.
 */
public class MainActivity extends Activity {

    private int count;
    private TextView counterView;
    private TextView titleView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.rgb(18, 8, 31));
        root.setPadding(32, 32, 32, 32);

        titleView = new TextView(this);
        titleView.setText("__APP__");
        titleView.setTextColor(Color.rgb(192, 132, 252));
        titleView.setTextSize(28);
        titleView.setGravity(Gravity.CENTER);

        counterView = new TextView(this);
        counterView.setText("0");
        counterView.setTextColor(Color.rgb(237, 233, 254));
        counterView.setTextSize(56);
        counterView.setGravity(Gravity.CENTER);
        counterView.setPadding(0, 40, 0, 40);

        Button plus = makeButton("+1", Color.rgb(124, 58, 237));
        plus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                count++;
                refresh();
            }
        });

        Button minus = makeButton("-1", Color.rgb(59, 15, 120));
        minus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                count--;
                refresh();
            }
        });

        Button reset = makeButton("Сброс", Color.rgb(36, 18, 63));
        reset.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                count = 0;
                refresh();
            }
        });

        root.addView(titleView);
        root.addView(counterView);
        root.addView(plus);
        root.addView(minus);
        root.addView(reset);
        setContentView(root);
    }

    private Button makeButton(String label, int bgColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.rgb(237, 233, 254));
        b.setBackgroundColor(bgColor);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 12;
        b.setLayoutParams(lp);
        return b;
    }

    private void refresh() {
        counterView.setText(String.valueOf(count));
    }
}
