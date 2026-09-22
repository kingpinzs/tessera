package app.tileshell.qa.imefixture

/**
 * E9's tap-through screen: `top_field` at the top, `bottom_field` anchored to the bottom of the
 * window, the window set to adjustNothing so the keyboard never moves the bottom field. Not a
 * launcher; started explicitly: `am start -n app.tileshell.qa.imefixture/.BottomFieldActivity`.
 */
class BottomFieldActivity : FixtureActivity(R.layout.activity_bottom_field)
