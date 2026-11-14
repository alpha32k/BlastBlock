package com.blastblock.game.presentation.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.blastblock.game.BlastBlockApplication;
import com.blastblock.game.R;
import com.blastblock.game.domain.model.ScoreRecord;
import com.blastblock.game.presentation.state.ScoresUiState;
import com.blastblock.game.presentation.viewmodel.ScoresViewModel;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Displays completed games one page at a time. */
public final class ScoresActivity extends AppCompatActivity {

    private ScrollView scrollView;
    private LinearLayout listContainer;
    private TextView messageText;
    private Button previousPage;
    private Button nextPage;
    private Button retryButton;

    private NumberFormat pointsFormat;
    private DateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scores);
        SystemBarInsets.apply(this, findViewById(R.id.scores_content));

        scrollView = findViewById(R.id.scores_scroll);
        listContainer = findViewById(R.id.scores_list);
        messageText = findViewById(R.id.scores_message);
        previousPage = findViewById(R.id.scores_previous);
        nextPage = findViewById(R.id.scores_next);
        retryButton = findViewById(R.id.scores_retry);
        Button backButton = findViewById(R.id.scores_back);
        backButton.setOnClickListener(v -> finish());

        // Biçimlendiriciler bir kez kurulur. Tarih, cihazın saat diliminde gösterilir.
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        pointsFormat = NumberFormat.getIntegerInstance(locale);
        dateFormat = new SimpleDateFormat(getString(R.string.scores_date_format), locale);
        dateFormat.setTimeZone(TimeZone.getDefault());

        // ViewModel, uygulamanın bağımlılık kabındaki fabrika ile kurulur (bkz. AppViewModelFactory).
        BlastBlockApplication app = (BlastBlockApplication) getApplication();
        ScoresViewModel viewModel = new ViewModelProvider(this,
                app.getContainer().getViewModelFactory()).get(ScoresViewModel.class);

        previousPage.setOnClickListener(v -> viewModel.previousPage());
        nextPage.setOnClickListener(v -> viewModel.nextPage());
        retryButton.setOnClickListener(v -> viewModel.refresh());
        viewModel.getUiState().observe(this, this::render);
        if (viewModel.getUiState().getValue() == null) viewModel.refresh();
    }

    /** Durumu ekrana yansıtır: hata → hata metni, boş liste → bilgi metni, aksi halde satırlar. */
    private void render(ScoresUiState state) {
        if (state == null) {
            return;
        }
        List<ScoreRecord> scores = state.getScores();
        boolean empty = scores == null || scores.isEmpty();
        previousPage.setVisibility(state.getOffset() > 0 ? View.VISIBLE : View.GONE);
        previousPage.setEnabled(!state.isLoading());
        nextPage.setVisibility(state.hasNextPage() ? View.VISIBLE : View.GONE);
        retryButton.setVisibility(state.hasError() ? View.VISIBLE : View.GONE);

        if (state.hasError()) {
            showMessage(R.string.scores_load_failed);
        } else if (empty && state.isLoading()) {
            // İlk yükleme sürerken "boş" yazısını göstermeyelim; kısa süreli boş ekran yeterli.
            scrollView.setVisibility(View.GONE);
            messageText.setVisibility(View.GONE);
        } else if (empty) {
            showMessage(R.string.scores_empty);
        } else {
            messageText.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);
            fillList(scores, state.getOffset());
            scrollView.scrollTo(0, 0);
        }
    }

    /** Liste yerine tek bir bilgi metni gösterir. */
    private void showMessage(int textRes) {
        scrollView.setVisibility(View.GONE);
        messageText.setText(textRes);
        messageText.setVisibility(View.VISIBLE);
    }

    private void fillList(List<ScoreRecord> scores, long offset) {
        listContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < scores.size(); i++) {
            ScoreRecord record = scores.get(i);
            long rank = offset + i + 1;
            View row = inflater.inflate(R.layout.item_score, listContainer, false);

            TextView rankText = row.findViewById(R.id.score_rank);
            TextView pointsText = row.findViewById(R.id.score_points);
            TextView dateText = row.findViewById(R.id.score_date);
            TextView modeText = row.findViewById(R.id.score_mode);

            rankText.setText(getString(R.string.scores_rank, pointsFormat.format(rank)));
            rankText.setTextColor(ContextCompat.getColor(this, rankColor(rank)));
            pointsText.setText(getString(R.string.scores_points,
                    pointsFormat.format(record.getScore())));
            dateText.setText(dateFormat.format(new Date(record.getFinishedAtMillis())));
            modeText.setText(record.isDaily() ? R.string.menu_daily : R.string.scores_classic);

            listContainer.addView(row);
        }
    }

    /** İlk üç sıra vurgulu renkte (amber, turkuaz, mercan); diğerleri ikincil metin renginde. */
    private static int rankColor(long rank) {
        if (rank == 1) return R.color.bb_amber;
        if (rank == 2) return R.color.bb_turquoise;
        if (rank == 3) return R.color.bb_coral;
        return R.color.bb_text_secondary;
    }
}
