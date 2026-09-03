package com.smalegon.scanpdf

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Aviso educativo que se muestra la primera vez que el usuario toca "Escanear documento".
 *
 * Explica que, al terminar de escanear una página, el ícono ➕ agrega otra página,
 * mientras que "Siguiente" cierra el escaneo por completo — dos botones que se prestan
 * a confusión en la pantalla de escaneo de Google (esa pantalla la dibuja el SDK de
 * ML Kit y no se puede modificar desde la app; ver MainActivity.startScan()).
 *
 * [onContinue] se llama solo cuando el usuario toca "Entendido, empezar a escanear"
 * (no al cerrar el aviso deslizando o tocando afuera) — así el escáner solo se abre
 * por una acción explícita.
 */
class AddPageTipDialogFragment(
    private val onContinue: () -> Unit
) : BottomSheetDialogFragment() {

    private var pulseAnimator: AnimatorSet? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_add_page_tip, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btnGotIt).setOnClickListener {
            dismiss()
            onContinue()
        }

        startPulseAnimation(view.findViewById(R.id.pulseRing))
    }

    override fun onDestroyView() {
        // Evita que la animación en loop siga corriendo (y filtre memoria) después de cerrar el aviso.
        pulseAnimator?.cancel()
        pulseAnimator = null
        super.onDestroyView()
    }

    /** Aro que "respira": crece y se desvanece en loop para resaltar el botón de la ilustración. */
    private fun startPulseAnimation(ring: View) {
        val scaleX = ObjectAnimator.ofFloat(ring, View.SCALE_X, 1f, 1.3f)
        val scaleY = ObjectAnimator.ofFloat(ring, View.SCALE_Y, 1f, 1.3f)
        val alpha = ObjectAnimator.ofFloat(ring, View.ALPHA, 0.9f, 0f)

        pulseAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = 1100
            interpolator = LinearInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Reinicia el pulso (equivalente a repeatCount infinito para un AnimatorSet).
                    ring.scaleX = 1f
                    ring.scaleY = 1f
                    ring.alpha = 0.9f
                    if (view != null) start()
                }
            })
            start()
        }
    }
}
