package com.example.korunner
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import java.util.concurrent.CopyOnWriteArrayList
import java.util.Random

//classes

data class Cloud(var x: Float, var y: Float, val width: Float, val height: Float, val speed: Float)

class GameButton(private val text: String, private val color: Int, private val textColor: Int = Color.WHITE) {
    private val rect = RectF()
    private val shadowRect = RectF()

    fun setRect(x: Float, y: Float, w: Float, h: Float) {
        rect.set(x, y, x + w, y + h)
        shadowRect.set(x, y + 10, x + w, y + h + 10)
    }

    fun draw(canvas: Canvas, paint: Paint) {
        paint.color = Color.parseColor("#33000000")
        canvas.drawRoundRect(shadowRect, 15f, 15f, paint)
        paint.color = color
        canvas.drawRoundRect(rect, 15f, 15f, paint)
        paint.color = textColor
        paint.textSize = 50f
        paint.textAlign = Paint.Align.CENTER
        val offset = (paint.descent() + paint.ascent()) / 2
        canvas.drawText(text, rect.centerX(), rect.centerY() - offset, paint)
    }

    fun contains(x: Float, y: Float): Boolean = rect.contains(x, y)
}

//enums
enum class GameState {
    HOME, DIFFICULTY_SELECT, PLAYING, GAME_OVER
}

enum class Difficulty(val baseSpeed: Float, val baseSpawnRate: Long) {
    EASY(15f, 2200L),
    MEDIUM(22f, 1800L),
    HARD(30f, 1300L)
}

//main activity
class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        hideSystemUI()
        gameView = GameView(this)
        setContentView(gameView)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (gameView.currentState == GameState.PLAYING || gameView.currentState == GameState.DIFFICULTY_SELECT) {
            gameView.currentState = GameState.HOME
        } else {
            super.onBackPressed()
        }
    }
}

//view
class GameView(context: Context) : SurfaceView(context), Runnable, SurfaceHolder.Callback {

    private var thread: Thread? = null
    @Volatile private var isRunning = false
    private val surfaceHolder: SurfaceHolder = holder
    private val paint = Paint()

    // Optimization
    private val collisionRect = RectF()
    private var skyShader: Shader? = null

    // State
    var currentState = GameState.HOME
    private var selectedDifficulty = Difficulty.MEDIUM

    // Dimensions
    private var screenW = 0f
    private var screenH = 0f
    private var groundLevel = 0f

    // Physics & Logic
    private var gravity = 2.2f
    private val jumpStrength = -48f
    private var currentSpeed = 0f // Speeds up over time

    // Player
    private val playerSize = 100f
    private var playerX = 150f
    private var playerY = 0f
    private var velocityY = 0f
    private var isJumping = false

    // Entities
    private val obstacles = CopyOnWriteArrayList<RectF>()
    private val clouds = CopyOnWriteArrayList<Cloud>()

    // Spawning Logic
    private var lastObstacleTime = 0L
    private var nextSpawnDelay = 0L // Randomized delay
    private var score = 0L
    private var scoreTimer = 0L
    private val random = Random()

    // UI
    private val btnPlay = GameButton("PLAY", Color.parseColor("#4CAF50"))
    private val btnQuit = GameButton("QUIT", Color.parseColor("#F44336"))
    private val btnEasy = GameButton("EASY", Color.parseColor("#8BC34A"))
    private val btnMed = GameButton("MEDIUM", Color.parseColor("#FF9800"))
    private val btnHard = GameButton("HARD", Color.parseColor("#F44336"))
    private val btnHome = GameButton("HOME", Color.WHITE, Color.BLACK)

    init {
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) { start() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) { }
    override fun surfaceDestroyed(holder: SurfaceHolder) { stop() }

    override fun run() {
        while (isRunning) {
            val startTime = System.nanoTime()
            if (surfaceHolder.surface.isValid) {
                try {
                    val canvas = surfaceHolder.lockCanvas()
                    if (canvas != null) {
                        synchronized(holder) {
                            update()
                            renderFrame(canvas)
                        }
                        surfaceHolder.unlockCanvasAndPost(canvas)
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            // Cap at 60 FPS
            val frameTimeMillis = (System.nanoTime() - startTime) / 1_000_000
            if (frameTimeMillis < 16) {
                try { Thread.sleep(16 - frameTimeMillis) } catch (_: Exception) {}
            }
        }
    }

    private fun update() {
        if (screenW > 0) updateClouds()

        if (currentState == GameState.PLAYING) {
            // Gravity
            velocityY += gravity
            playerY += velocityY
            if (playerY + playerSize >= groundLevel) {
                playerY = groundLevel - playerSize
                velocityY = 0f
                isJumping = false
            }

            // logic
            if (System.currentTimeMillis() - lastObstacleTime > nextSpawnDelay) {
                spawnObstaclePattern()
                // Randomize next spawn time significantly
                // Base rate minus a random amount to make it unpredictable
                val variety = random.nextInt(1000)
                nextSpawnDelay = selectedDifficulty.baseSpawnRate - (currentSpeed * 10).toLong() + variety
                if (nextSpawnDelay < 800) nextSpawnDelay = 800 // Cap minimum delay
                lastObstacleTime = System.currentTimeMillis()
            }

            // Move Obstacles
            for (obs in obstacles) {
                obs.left -= currentSpeed
                obs.right -= currentSpeed

                // Collision
                collisionRect.set(playerX + 20, playerY + 20, playerX + playerSize - 20, playerY + playerSize - 10)
                if (RectF.intersects(obs, collisionRect)) currentState = GameState.GAME_OVER
                if (obs.right < 0) obstacles.remove(obs)
            }

            // Score & Speed Up
            if (System.currentTimeMillis() - scoreTimer > 100) {
                score++
                scoreTimer = System.currentTimeMillis()
                // Slowly increase speed every 5 points
                if (score % 5 == 0L) currentSpeed += 0.2f
            }
        }
    }

    private fun spawnObstaclePattern() {
        if (screenW == 0f) return

        val obsH = 90f
        val obsW = 70f
        val startX = screenW + 50f

        // 0-100 Roll
        val roll = random.nextInt(100)

        // Pattern 1
        if (roll < 30) {
            obstacles.add(RectF(startX, groundLevel - obsH, startX + obsW, groundLevel))
            val secondX = startX + obsW // Right next to it
            obstacles.add(RectF(secondX, groundLevel - obsH, secondX + obsW, groundLevel))
        }
        // Pattern 2
        else if (roll < 50) {
            obstacles.add(RectF(startX, groundLevel - obsH, startX + obsW, groundLevel))
            val gap = 180f // Jumpable gap
            val secondX = startX + obsW + gap
            obstacles.add(RectF(secondX, groundLevel - obsH, secondX + obsW, groundLevel))
        }
        // Pattern 3
        else {
            obstacles.add(RectF(startX, groundLevel - obsH, startX + obsW, groundLevel))
        }
    }

    private fun updateClouds() {
        if (clouds.isEmpty() || random.nextInt(100) < 2) {
            val w = random.nextInt(200) + 150f
            val h = random.nextInt(60) + 40f
            val x = screenW + 50f
            val y = random.nextInt((screenH / 2).toInt()).toFloat()
            val speed = random.nextFloat() * 2 + 1
            clouds.add(Cloud(x, y, w, h, speed))
        }
        for (cloud in clouds) {
            cloud.x -= cloud.speed
            if (cloud.x + cloud.width < 0) clouds.remove(cloud)
        }
    }

    private fun renderFrame(canvas: Canvas) {
        if (screenW == 0f) {
            screenW = width.toFloat()
            screenH = height.toFloat()
            groundLevel = screenH - 100f
            layoutButtons()
            skyShader = LinearGradient(0f, 0f, 0f, screenH, Color.parseColor("#4FC3F7"), Color.parseColor("#E1F5FE"), Shader.TileMode.CLAMP)
        }

        // Sky
        if (skyShader != null) {
            paint.shader = skyShader
            canvas.drawRect(0f, 0f, screenW, screenH, paint)
            paint.shader = null
        } else canvas.drawColor(Color.CYAN)

        // Clouds
        paint.color = Color.parseColor("#AAFFFFFF")
        for (cloud in clouds) canvas.drawOval(cloud.x, cloud.y, cloud.x + cloud.width, cloud.y + cloud.height, paint)

        // Ground
        paint.color = Color.parseColor("#5D4037")
        canvas.drawRect(0f, groundLevel, screenW, screenH, paint)
        paint.color = Color.parseColor("#388E3C")
        canvas.drawRect(0f, groundLevel, screenW, groundLevel + 30f, paint)

        when (currentState) {
            GameState.HOME -> drawHomeScreen(canvas)
            GameState.DIFFICULTY_SELECT -> drawDifficultyScreen(canvas)
            GameState.PLAYING -> drawGame(canvas)
            GameState.GAME_OVER -> drawGameOver(canvas)
        }
    }

    private fun drawGame(canvas: Canvas) {
        drawDuck(canvas, playerX, playerY, playerSize)
        for (obs in obstacles) drawMushroom(canvas, obs)

        paint.textSize = 60f
        paint.textAlign = Paint.Align.LEFT
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = Color.WHITE
        canvas.drawText("SCORE: $score", 50f, 80f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        canvas.drawText("SCORE: $score", 50f, 80f, paint)
    }

    private fun drawDuck(canvas: Canvas, x: Float, y: Float, size: Float) {
        paint.color = Color.parseColor("#FFEB3B")
        canvas.drawRoundRect(x, y + 20, x + size, y + size, 10f, 10f, paint) // Body
        canvas.drawRect(x + 20, y - 10, x + size - 10, y + 40, paint) // Head
        paint.color = Color.parseColor("#FBC02D")
        canvas.drawOval(x + 10, y + 40, x + 60, y + 70, paint) // Wing
        paint.color = Color.parseColor("#FF5722")
        canvas.drawRect(x + size - 10, y + 10, x + size + 20, y + 30, paint) // Beak
        paint.color = Color.BLACK
        canvas.drawCircle(x + size - 25, y + 10, 5f, paint) // Eye
    }

    private fun drawMushroom(canvas: Canvas, rect: RectF) {
        paint.color = Color.parseColor("#FFF3E0")
        val stemW = rect.width() / 2
        canvas.drawRect(rect.centerX() - stemW/2, rect.top + 30, rect.centerX() + stemW/2, rect.bottom, paint)
        paint.color = Color.parseColor("#D32F2F")
        canvas.drawArc(rect.left - 10, rect.top, rect.right + 10, rect.bottom - 20, 180f, 180f, true, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(rect.left + 20, rect.top + 25, 6f, paint)
        canvas.drawCircle(rect.right - 20, rect.top + 15, 8f, paint)
    }

    private fun drawHomeScreen(canvas: Canvas) {
        centerText(canvas, "KOrunner", screenH * 0.3f, 140f, Color.parseColor("#FFC107"), true)
        centerText(canvas, "Made By ICEY067", screenH * 0.4f, 40f, Color.WHITE, true)
        btnPlay.draw(canvas, paint)
        btnQuit.draw(canvas, paint)
    }

    private fun drawDifficultyScreen(canvas: Canvas) {
        centerText(canvas, "SELECT DIFFICULTY", screenH * 0.2f, 80f, Color.WHITE, true)
        btnEasy.draw(canvas, paint)
        btnMed.draw(canvas, paint)
        btnHard.draw(canvas, paint)
    }

    private fun drawGameOver(canvas: Canvas) {
        drawGame(canvas)
        canvas.drawColor(Color.argb(180, 0, 0, 0))
        centerText(canvas, "GAME OVER", screenH * 0.35f, 120f, Color.WHITE, true)
        centerText(canvas, "Final Score: $score", screenH * 0.45f, 60f, Color.parseColor("#FFC107"), true)
        btnHome.draw(canvas, paint)
    }

    private fun layoutButtons() {
        val cx = screenW / 2
        val btnW = 400f
        val btnH = 90f

        btnPlay.setRect(cx - btnW/2, screenH * 0.55f, btnW, btnH)
        btnQuit.setRect(cx - btnW/2, screenH * 0.75f, btnW, btnH)

        val gap = 30f
        val startY = screenH * 0.3f
        btnEasy.setRect(cx - btnW/2, startY, btnW, btnH)
        btnMed.setRect(cx - btnW/2, startY + btnH + gap, btnW, btnH)
        btnHard.setRect(cx - btnW/2, startY + (btnH + gap) * 2, btnW, btnH)
        btnHome.setRect(cx - btnW/2, screenH * 0.6f, btnW, btnH)
    }

    private fun centerText(canvas: Canvas, text: String, y: Float, size: Float, color: Int, shadow: Boolean) {
        paint.textSize = size
        paint.textAlign = Paint.Align.CENTER
        if (shadow) {
            paint.color = Color.BLACK
            canvas.drawText(text, screenW / 2 + 5, y + 5, paint)
        }
        paint.color = color
        canvas.drawText(text, screenW / 2, y, paint)
    }

    private fun resetGame(diff: Difficulty) {
        selectedDifficulty = diff
        currentSpeed = diff.baseSpeed
        playerY = groundLevel - playerSize
        velocityY = 0f
        isJumping = false
        obstacles.clear()
        score = 0
        lastObstacleTime = System.currentTimeMillis()
        nextSpawnDelay = 1000L
        currentState = GameState.PLAYING
    }

    override fun performClick(): Boolean { super.performClick(); return true }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            performClick()
            val x = event.x
            val y = event.y

            when (currentState) {
                GameState.HOME -> {
                    if (btnPlay.contains(x, y)) currentState = GameState.DIFFICULTY_SELECT
                    if (btnQuit.contains(x, y)) { (context as Activity).finish(); System.exit(0) }
                }
                GameState.DIFFICULTY_SELECT -> {
                    if (btnEasy.contains(x, y)) resetGame(Difficulty.EASY)
                    if (btnMed.contains(x, y)) resetGame(Difficulty.MEDIUM)
                    if (btnHard.contains(x, y)) resetGame(Difficulty.HARD)
                }
                GameState.PLAYING -> {
                    if (!isJumping) { velocityY = jumpStrength; isJumping = true }
                }
                GameState.GAME_OVER -> {
                    if (btnHome.contains(x, y)) currentState = GameState.HOME
                }
            }
        }
        return true
    }

    fun start() { if (!isRunning) { isRunning = true; thread = Thread(this); thread?.start() } }
    fun stop() { isRunning = false; try { thread?.join() } catch (_: Exception) {} }
}