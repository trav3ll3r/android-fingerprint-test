package au.com.trav3ll3r.playground.tabnav

import android.content.Context
import android.support.annotation.ColorRes
import android.support.annotation.DrawableRes
import android.support.annotation.StringRes
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import au.com.trav3ll3r.playground.R
import org.jetbrains.anko.find
import org.jetbrains.anko.textColor
import org.jetbrains.anko.textResource

class TabCustomView
@JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : LinearLayout(context, attrs, defStyleAttr) {

    private var textView: TextView
    private var imageView: ImageView

    init {
        View.inflate(context, R.layout.widget_tab_custom_view, this)

        textView = find(R.id.tab_text)
        imageView = find(R.id.tab_icon)

        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        setTabSelected(false)
    }

    fun setText(@StringRes stringId: Int) {
        textView.textResource = stringId
    }

    fun setTextColor(@ColorRes color: Int) {
        textView.textColor = ContextCompat.getColor(context, color)
    }

    fun setTextColorStateList(@ColorRes color: Int) {
        textView.setTextColor(ContextCompat.getColorStateList(context, color))
    }

    fun setIcon(@DrawableRes drawableId: Int) {
        imageView.setImageDrawable(ContextCompat.getDrawable(context, drawableId))
    }

    fun setTabSelected(selected: Boolean) {
        // SET normal OR bold FONT TYPE FACE
        //textView.typeface = if (selected) Typefaces.get(R.string.font_semi_bold) else Typefaces.get(R.string.font_regular)
        textView.isSelected = selected
    }

    fun setShowIcon(showIcon: Boolean) {
        imageView.visibility = if (showIcon) View.VISIBLE else View.GONE
        requestLayout()
    }
}