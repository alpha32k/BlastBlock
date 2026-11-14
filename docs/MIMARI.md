# Blast Block — Mimari

Blast Block, **katmanlı mimari** ve **MVVM** (Model–View–ViewModel) desenine göre üç Gradle modülüne ayrılmıştır.

```
┌───────────────────────────── :app (sunum katmanı) ─────────────────────────────┐
│  MenuActivity, GameActivity, GameView (Canvas)  ──►  MenuViewModel, GameViewModel │
│  AppContainer (bağımlılıkları kurar)                                              │
└───────────────┬──────────────────────────────────────────────┬───────────────────┘
                │ kullanır                                     │ kurar
                ▼                                              ▼
┌──────── :domain (iş kuralları, saf Java) ────────┐   ┌──── :data (kayıt katmanı) ────┐
│ Board, Piece, GameState, PieceCatalog            │◄──│ FileGameRepository (JSON)      │
│ GameEngine, ScoreCalculator, FairPieceGenerator  │   │ PrefsHighScoreRepository       │
│ GameRepository / HighScoreRepository (arayüz)    │   │ PrefsSettingsRepository        │
└──────────────────────────────────────────────────┘   └────────────────────────────────┘
```

Bağımlılık yönü: `:app → :data → :domain` ve `:app → :domain`. `:domain` hiçbir modüle ve Android'e bağlı değildir;
bu kural Gradle tarafından zorlanır (domain modülü `java-library` olduğu için Android sınıflarını göremez).

`domain.usecase.GameSession` kayıt açma, kaydetme ve oyun sonu iş akışlarını yönetir. Depo arayüzlerini kullanır ve sonucu sunuma taşımak için Android sınıflarına ihtiyaç duymaz. `GameViewModel` sonuçları ekran durumuna ve olaylarına çevirir.

Bir hamlenin yolculuğu:
1. Oyuncu parçayı sürükler; `GameView` parmağın konumunu tahta hücresine çevirir ve `GameViewModel.preview(...)` ile önizleme ister.
2. Parmak kalkınca `GameViewModel.place(slot, satır, sütun)` çağrılır.
3. ViewModel, `GameEngine.place(...)` ile kuralları uygular: yerleştirme → dolu satır/sütunları silme → puan → gerekirse yeni üçlü → oyun sonu kontrolü.
4. Yeni durum `LiveData` ile ekrana yayınlanır; animasyon ve titreşim için tek seferlik bir olay gönderilir.
5. Durum arka planda tek sıralı bir iş parçacığında `GameRepository.save(...)` ile diske yazılır.

## Domain sözleşmesi (paket `com.blastblock.game.domain`)

Tüm üyeler `public`. Model nesneleri **değişmez** (immutable); diziler girişte ve çıkışta kopyalanır.
Koordinatlar `(satır, sütun)` sırasıyla ve 0 tabanlıdır. Hücre değeri `0` = boş, `1..5` = renk kimliği.

| Sınıf | İmzalar |
|---|---|
| `model.Board` | `static final int SIZE = 8`; `Board(int[] cells)` (64 eleman, satır-öncelikli: indeks = satır*8+sütun; geçersizse `IllegalArgumentException`); `static Board empty()`; `int getCell(int row, int col)`; `int[] getCells()`; `boolean isEmpty(int row, int col)` |
| `model.Piece` | `Piece(String shapeId, int colorId, int[][] cells)` (cells: `{satır, sütun}` çiftleri, en küçük satır ve sütun 0 olacak şekilde normalize); `String getShapeId()`; `int getColorId()`; `int[][] getCells()`; `int getWidth()`; `int getHeight()`; `int getCellCount()` |
| `model.GameState` | `GameState(Board board, Piece[] tray, long score, int streak, boolean gameOver)` (tray uzunluğu 3, `null` = kullanılmış yuva; streak 0..5); `Board getBoard()`; `Piece[] getTray()`; `Piece getTrayPiece(int slot)`; `long getScore()`; `int getStreak()`; `boolean isGameOver()` |
| `catalog.PieceCatalog` | `static final int COLOR_COUNT = 5`; `static List<String> ids()`; `static Piece create(String shapeId, int colorId)` (bilinmeyen kimlik → `IllegalArgumentException`) |
| `engine.ScoreCalculator` | `static long placementPoints(int cells)` = 10·N; `static long clearPoints(int lines, int multiplier)` = (100·L + 50·max(0, L−1))·çarpan; `static int nextStreak(int streak, int lines)` (lines>0 → min(streak+1, 5), değilse 0) |
| `engine.FairPieceGenerator` | `FairPieceGenerator(Random random)`; `Piece[] nextTray(Board board)` |
| `engine.MovePreview` | `static MovePreview invalid()`; `boolean isValid()`; `int[] getCompletedRows()`; `int[] getCompletedColumns()` |
| `engine.MoveResult` | `boolean isPlaced()`; `GameState getState()`; `Board getPlacedBoard()` (silme öncesi tahta); `long getScoreDelta()`; `int[] getClearedRows()`; `int[] getClearedColumns()`; `int getLinesCleared()`; `int getMultiplier()`; `boolean isNewTray()` |
| `engine.GameEngine` | `GameEngine(Random random)`; `GameState newGame()`; `GameState restore(GameState snapshot)` (tutarsızsa `IllegalArgumentException`); `MovePreview preview(GameState state, int slot, int row, int col)`; `MoveResult place(GameState state, int slot, int row, int col)`; `boolean hasAnyMove(GameState state)` |
| `repository.GameRepository` | `GameLoadResult load() throws IOException`; `void save(GameState state) throws IOException`; `void clearSavedGame() throws IOException` |
| `repository.GameLoadResult` | `enum Status { EMPTY, ACTIVE, INVALID }`; `static GameLoadResult empty()`; `static GameLoadResult active(GameState state)`; `static GameLoadResult invalid()`; `Status getStatus()`; `GameState getState()` (yalnızca ACTIVE'de dolu) |
| `repository.HighScoreRepository` | `long getHighScore()`; `boolean submitScore(long score)` (yeni rekorsa kaydeder ve true döner) |
| `repository.SettingsRepository` | `boolean isVibrationEnabled()` (varsayılan true); `void setVibrationEnabled(boolean enabled)` |

Davranış kuralları:
- `place` ve `preview`, `(row, col)` noktasını parçanın `(0,0)` göreli hücresinin konacağı tahta hücresi olarak yorumlar.
- Geçersiz `place` (dolu hücre, tahta dışı, boş yuva, oyun bitmiş) → `isPlaced()==false`, aynı durum, puan 0.
- Geçerli `place`: yerleştir → dolu satır ve sütunları **aynı tahtadan birlikte** bul → birlikte sil (kesişim hücresi bir kez silinir, satır ve sütun ayrı çizgi sayılır) → puan → seri → üç yuva da boşsa yeni üçlü → hiçbir kalan parça sığmıyorsa `gameOver=true`.
- Puan: `placementPoints(parça hücre sayısı) + clearPoints(L, çarpan)`, çarpan = yeni seri değeri (ilk temizleme ×1, art arda ×2…×5); temizlemesiz hamlede seri 0 olur. Örnek: 4 hücre + 2 çizgi → ilk 40+250=290, art arda 40+500=540.
- Adil üretim: yeni üçlüde bir parça o an tahtaya sığan şekillerden, ikisi tüm katalogdan seçilir; yuvalar karıştırılır; renkler 1..5 arasından rastgele.
- `preview` durumu ve rastgele sayı üretecini değiştirmez.
- Çizgi silinmeyen hamlede `MoveResult.getMultiplier()` 0 döner; geçersiz hamlede `getPlacedBoard()` mevcut tahtadır.
- `restore`, tepsideki parçaları katalogla karşılaştırır (uyuşmazsa `IllegalArgumentException`); oyun bitmemiş görünüp hiçbir parça sığmıyorsa durumu `gameOver=true` yaparak döndürür.
- Ek sabitler: `Board.isInside(row, col)`, `GameState.TRAY_SIZE = 3`, `GameState.MAX_STREAK = 5`, `ScoreCalculator.MAX_MULTIPLIER = 5`. Katalog 31 şekil içerir.
- `PieceCatalog.create` bilinmeyen kimlikte `IllegalArgumentException` atar; kayıt okuyucu bunu yakalayıp `INVALID` döndürmelidir.

### v1.1 eklemeleri (domain)
| Sınıf | İmzalar |
|---|---|
| `engine.PlacementTarget` | değişmez; `static PlacementTarget invalid()`; `static PlacementTarget at(int row, int col, boolean snapped)`; `boolean isValid()`; `int getRow()`; `int getCol()`; `boolean isSnapped()` (doğrudan hedef değil, yakına kaydırıldı); `equals/hashCode` |
| `engine.PlacementResolver` | `static final float DEFAULT_RADIUS = 1.5f`; `static PlacementTarget resolve(Board board, Piece piece, float fracRow, float fracCol, float radius, PlacementTarget previous)` |
| `model.ScoreRecord` | Tamamlanan oyunun puanı, bitiş zamanı ve klasik/günlük modu. |
| `repository.ScoreHistoryRepository` | `getScores(long offset, int limit)` bir sayfa okur. `addScore(ScoreRecord)` her tamamlanan oyunu ekler ve varsa kutlama için sırasını döndürür. |
| `data.SqliteScoreHistoryRepository` | Tamamlanan oyunları SQLite'a ekler. İlk açılışta geçerli eski JSON skorlarını içeri aktarır. |

`PlacementResolver.resolve` kuralları: `fracRow/fracCol` parçanın sol-üst hücresinin kesirli tahta koordinatıdır. (1) Yuvarlanmış hedef geçerliyse onu döndürür (`snapped=false`). (2) Değilse, sol-üst başlangıç noktası (r,c) için Öklid uzaklığı `sqrt((r-fracRow)²+(c-fracCol)²) ≤ radius` olan ve parçanın tamamen sığdığı adaylar arasından en yakını seçilir (`snapped=true`). Eşitlikte `previous` geçerli ve aday kümesindeyse o; yoksa küçük satır, sonra küçük sütun. (3) Aday yoksa `invalid()`. Board/piece'i değiştirmez, kopya dizi üretmemeye özen gösterir.

## Sunum sözleşmesi (paket `com.blastblock.game.presentation`)

| Tür | İmzalar |
|---|---|
| `viewmodel.GameViewModel` | `GameViewModel(GameEngine engine, GameRepository gameRepository, HighScoreRepository highScoreRepository, Executor ioExecutor, Executor mainExecutor)` (arka plan sonuçları mainExecutor ile ana iş parçacığında, oturum kontrolü teslim anında yapılarak yayınlanır); `LiveData<GameUiState> getUiState()`; `LiveData<Event<UiEvent>> getEvents()`; `void open(boolean startNew)`; `MovePreview preview(int slot, int row, int col)`; `void place(int slot, int row, int col)`; `void newGame()`; `void saveNow()` |
| `viewmodel.MenuViewModel` | `MenuViewModel(GameRepository gameRepository, HighScoreRepository highScoreRepository, SettingsRepository settingsRepository, Executor ioExecutor, Executor mainExecutor)`; `LiveData<MenuUiState> getUiState()`; `void refresh()`; `void toggleVibration()` |
| `state.GameUiState` | `GameState getGame()`; `long getHighScore()`; `boolean isLoading()` |
| `state.MenuUiState` | `long getHighScore()`; `boolean hasSavedGame()`; `boolean isVibrationEnabled()` |
| `state.UiEvent` | `enum Type { MOVE_COMMITTED, GAME_OVER, INVALID_SAVE, SAVE_FAILED, LOAD_FAILED }` (LOAD_FAILED: kayıt geçici olarak okunamadı; kayıt silinmez, ekran hata gösterip menüye döner); `Type getType()`; `MoveResult getMoveResult()` (MOVE_COMMITTED ve GAME_OVER'da dolu); `boolean isNewHighScore()` |
| `state.Event<T>` | `Event(T content)`; `T getContentIfNotHandled()` (bir kez döner, sonra null); `T peekContent()` |

### v1.1 eklemeleri (sunum)
| Tür | İmzalar |
|---|---|
| `viewmodel.GameViewModel` | Güncel kurucu: `GameViewModel(GameEngine engine, GameSession gameSession, Executor ioExecutor, Executor mainExecutor)`. `PlacementTarget resolveTarget(int slot, float fracRow, float fracCol, PlacementTarget previous)` durum yoksa, yükleniyorsa veya oyun bittiyse `invalid()` döndürür. UI çözülmüş hedefin satır ve sütununu `preview` ve `place` metotlarına verir. |
| `domain.usecase.GameSession` | Oyun sonu sırası: `submitScore` → `addScore(new ScoreRecord(score, clock.getAsLong()))` → bitmiş durumu `save` → `clearSavedGame`. Yüklerken bitmiş olduğu anlaşılan oyun da aynı iş akışından geçer. Listeye ekleme hatasında sıra 0 döner ve oyun sonu tamamlanır. |
| `state.UiEvent` | Ek: `int getTopRank()` (GAME_OVER'da 1..10, giremediyse 0) |
| `viewmodel.ScoresViewModel` | `getUiState()`, `refresh()`, `nextPage()` ve `previousPage()`. |
| `state.ScoresUiState` | Sayfanın kayıtları, sıra başlangıcı, sonraki sayfa, yükleme ve hata bilgisi. |

## Kaynak adları (orkestratör tarafından sağlanır)
- Metinler: `res/values/strings.xml` (ör. `menu_new_game`, `game_over_title`, `exit_title` …).
- Renkler: `res/values/colors.xml` — `bb_background`, `bb_board`, `bb_board_cell`, blok renkleri `bb_turquoise`(1), `bb_coral`(2), `bb_amber`(3), `bb_violet`(4), `bb_mint`(5), önizleme `bb_preview_valid/invalid`, `bb_line_highlight`.
- Ölçüler: `res/values/dimens.xml`.
- Görseller: `res/drawable-nodpi/bb_logo.png` (`@drawable/bb_logo`), `bb_menu_background.jpg` (`@drawable/bb_menu_background`), `bb_block_texture.png` (`@drawable/bb_block_texture`) (gri tonlu, kodda renge boyanır).
