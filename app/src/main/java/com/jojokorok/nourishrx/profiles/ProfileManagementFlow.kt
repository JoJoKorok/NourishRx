package com.jojokorok.nourishrx.profiles

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.jojokorok.nourishrx.data.MedicationStore
import com.jojokorok.nourishrx.data.Profile
import com.jojokorok.nourishrx.reminders.ReminderScheduler
import com.jojokorok.nourishrx.ui.NourishColors
import com.jojokorok.nourishrx.ui.NourishShapes
import com.jojokorok.nourishrx.ui.NourishSpacing
import com.jojokorok.nourishrx.ui.NourishTypography
import com.jojokorok.nourishrx.ui.NourishUi

class ProfileManagementFlow(
    private val activity: Activity,
    private val store: MedicationStore,
    private val ui: NourishUi,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun currentProfileId(): Long
        fun setSelectedProfileId(profileId: Long)
        fun renderShell()
        fun plural(count: Long, singular: String, plural: String): String
        fun profileAvatar(profile: Profile, sizeDp: Int, fallbackColor: Int, textSp: Int): View
        fun avatarWidthDp(profile: Profile, heightDp: Int): Int
        fun chooseProfilePhoto(profile: Profile)
        fun showProfilePhotoEditor(
            profile: Profile,
            avatarUri: String,
            zoom: Float,
            offsetX: Float,
            offsetY: Float,
            aspectRatio: Float
        )
    }

    fun showProfilesDialog() {
        val body = dialogBody().apply {
            addView(
                dialogHeader(
                    "Profiles",
                    "Medication and nutrition records stay together for each person."
                )
            )
        }
        val dialogRef = arrayOfNulls<AlertDialog>(1)
        store.getProfiles().forEach { profile ->
            body.addView(profileManagementRow(profile, dialogRef))
        }

        body.addView(
            ui.button("Add profile", NourishColors.ON_ACCENT, NourishColors.GREEN).apply {
                setSingleLine(true)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    showAddProfileDialog()
                }
            },
            matchParams(height = 48, topMargin = NourishSpacing.MD)
        )

        val scrollView = ScrollView(activity).apply {
            isFillViewport = true
            addView(body)
        }
        dialogRef[0] = AlertDialog.Builder(activity)
            .setView(scrollView)
            .setNegativeButton("Close", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener { styleDialogActions(dialog) }
                dialog.show()
                dialog.window?.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
    }

    private fun profileManagementRow(
        profile: Profile,
        dialogRef: Array<AlertDialog?>
    ): LinearLayout = ui.card().apply {
        elevation = ui.dp(NourishShapes.ELEVATION_FLAT).toFloat()
        setPadding(
            ui.dp(NourishSpacing.MD),
            ui.dp(NourishSpacing.SM),
            ui.dp(NourishSpacing.MD),
            ui.dp(NourishSpacing.SM)
        )

        val selected = profile.id == callbacks.currentProfileId()
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val avatarSize = 46
        top.addView(
            callbacks.profileAvatar(
                profile,
                avatarSize,
                if (selected) NourishColors.GREEN else NourishColors.BLUE,
                15
            ),
            LinearLayout.LayoutParams(
                ui.dp(callbacks.avatarWidthDp(profile, avatarSize)),
                ui.dp(avatarSize)
            ).apply {
                rightMargin = ui.dp(NourishSpacing.SM)
            }
        )

        val medicationCount = store.getMedicationCountForProfile(profile.id).toLong()
        val labels = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.displayText(profile.name, NourishTypography.BODY_LARGE, NourishColors.INK))
            addView(
                ui.text(
                    if (selected) {
                        "Current profile - ${callbacks.plural(medicationCount, "medication", "medications")}"
                    } else {
                        callbacks.plural(medicationCount, "medication", "medications")
                    },
                    NourishTypography.CAPTION,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                matchWrapParams(topMargin = NourishSpacing.XXS)
            )
        }
        top.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        if (selected) {
            top.addView(currentBadge(), wrapWrapParams(startMargin = NourishSpacing.SM))
        } else {
            top.addView(
                ui.button("Switch", NourishColors.GREEN_DARK, NourishColors.GREEN_SOFT).apply {
                    setSingleLine(true)
                    contentDescription = "Switch to ${profile.name}"
                    setOnClickListener {
                        callbacks.setSelectedProfileId(profile.id)
                        dialogRef[0]?.dismiss()
                        callbacks.renderShell()
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ui.dp(42)).apply {
                    leftMargin = ui.dp(NourishSpacing.SM)
                }
            )
        }
        addView(top)

        addView(divider(), matchParams(height = 1, topMargin = NourishSpacing.SM))
        val actions = actionRow()
        actions.addView(
            ui.button(
                if (profile.hasAvatar()) "Photo" else "Add photo",
                NourishColors.GOLD,
                Color.TRANSPARENT
            ).apply {
                setSingleLine(true)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    showProfilePhotoOptions(profile)
                }
            },
            weightedActionParams()
        )
        actions.addView(
            ui.button("Rename", NourishColors.BLUE, Color.TRANSPARENT).apply {
                setSingleLine(true)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    showRenameProfileDialog(profile)
                }
            },
            weightedActionParams()
        )
        actions.addView(
            ui.button("Delete", NourishColors.CORAL, Color.TRANSPARENT).apply {
                setSingleLine(true)
                setOnClickListener {
                    dialogRef[0]?.dismiss()
                    confirmDeleteProfile(profile)
                }
            },
            weightedActionParams(last = true)
        )
        addView(actions)
    }

    private fun showProfilePhotoOptions(profile: Profile) {
        if (!profile.hasAvatar()) {
            callbacks.chooseProfilePhoto(profile)
            return
        }

        val body = dialogBody().apply {
            addView(
                dialogHeader(
                    "${profile.name}'s photo",
                    "Adjust the current framing, choose another image, or return to initials."
                )
            )
        }
        lateinit var dialog: AlertDialog
        body.addView(
            optionButton("Adjust framing", NourishColors.BLUE) {
                dialog.dismiss()
                callbacks.showProfilePhotoEditor(
                    profile,
                    profile.avatarUri,
                    profile.avatarZoom,
                    profile.avatarOffsetX,
                    profile.avatarOffsetY,
                    profile.avatarAspectRatio
                )
            },
            matchParams(height = 46, topMargin = NourishSpacing.MD)
        )
        body.addView(
            optionButton("Choose new photo", NourishColors.GREEN) {
                dialog.dismiss()
                callbacks.chooseProfilePhoto(profile)
            },
            matchParams(height = 46, topMargin = NourishSpacing.XS)
        )
        body.addView(
            optionButton("Remove photo", NourishColors.CORAL) {
                dialog.dismiss()
                store.clearProfileAvatar(profile.id)
                Toast.makeText(activity, "Profile photo removed.", Toast.LENGTH_SHORT).show()
                callbacks.renderShell()
            },
            matchParams(height = 46, topMargin = NourishSpacing.XS)
        )

        dialog = AlertDialog.Builder(activity)
            .setView(body)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.setOnShowListener { styleDialogActions(dialog) }
        dialog.show()
    }

    private fun showRenameProfileDialog(profile: Profile) {
        val body = dialogBody().apply {
            addView(dialogHeader("Rename profile", "Update how this person appears throughout the app."))
            addView(ui.fieldLabel("Profile name"))
        }
        val nameField = ui.field(
            "Person's name",
            profile.name,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
        body.addView(nameField)

        val dialog = AlertDialog.Builder(activity)
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            styleDialogActions(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    nameField.error = "Required"
                    return@setOnClickListener
                }
                store.renameProfile(profile.id, name)
                dialog.dismiss()
                callbacks.renderShell()
            }
            nameField.requestFocus()
            nameField.setSelection(nameField.text.length)
        }
        dialog.show()
    }

    private fun confirmDeleteProfile(profile: Profile) {
        if (store.getProfiles().size <= 1) {
            Toast.makeText(activity, "Keep at least one profile.", Toast.LENGTH_SHORT).show()
            return
        }

        val body = dialogBody().apply {
            addView(
                dialogHeader(
                    "Delete ${profile.name}?",
                    "This permanently removes this profile's medications, dose history, nutrition logs, meals, and tracking data from this phone."
                )
            )
        }
        val dialog = AlertDialog.Builder(activity)
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                store.getAllMedications(profile.id).forEach { medication ->
                    ReminderScheduler.cancel(activity, medication.id)
                }
                val deleted = store.deleteProfile(profile.id)
                if (deleted && callbacks.currentProfileId() == profile.id) {
                    store.getProfiles().firstOrNull()?.let { nextProfile ->
                        callbacks.setSelectedProfileId(nextProfile.id)
                    }
                }
                ReminderScheduler.scheduleAll(activity)
                callbacks.renderShell()
            }
            .create()
        dialog.setOnShowListener { styleDialogActions(dialog, destructive = true) }
        dialog.show()
    }

    private fun showAddProfileDialog() {
        val body = dialogBody().apply {
            addView(
                dialogHeader(
                    "New profile",
                    "Create one shared identity for medication and nutrition records."
                )
            )
            addView(ui.fieldLabel("Profile name"))
        }
        val nameField = ui.field(
            "Person's name",
            "",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        )
        body.addView(nameField)

        val dialog = AlertDialog.Builder(activity)
            .setView(body)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create()
        dialog.setOnShowListener {
            styleDialogActions(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isEmpty()) {
                    nameField.error = "Required"
                    return@setOnClickListener
                }
                val profileId = store.saveProfile(name)
                callbacks.setSelectedProfileId(profileId)
                dialog.dismiss()
                callbacks.renderShell()
            }
            nameField.requestFocus()
        }
        dialog.show()
    }

    private fun dialogBody(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(
            ui.dp(NourishSpacing.LG),
            ui.dp(NourishSpacing.MD),
            ui.dp(NourishSpacing.LG),
            ui.dp(NourishSpacing.SM)
        )
    }

    private fun dialogHeader(title: String, subtitle: String): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, ui.dp(NourishSpacing.XS))
            addView(ui.displayText(title, NourishTypography.TITLE, NourishColors.INK))
            addView(
                ui.text(
                    subtitle,
                    NourishTypography.LABEL,
                    NourishColors.MUTED,
                    Typeface.NORMAL
                ),
                matchWrapParams(topMargin = NourishSpacing.XXS)
            )
        }

    private fun currentBadge(): TextView =
        ui.text(
            "Current",
            NourishTypography.CAPTION,
            NourishColors.GREEN_DARK,
            Typeface.BOLD
        ).apply {
            gravity = Gravity.CENTER
            setPadding(
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.XXS),
                ui.dp(NourishSpacing.XS),
                ui.dp(NourishSpacing.XXS)
            )
            background = ui.rounded(
                NourishColors.GREEN_SOFT,
                Color.TRANSPARENT,
                ui.dp(NourishShapes.RADIUS_CONTROL)
            )
        }

    private fun optionButton(label: String, color: Int, action: () -> Unit): Button =
        ui.button(label, color, Color.TRANSPARENT).apply {
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { action() }
        }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(NourishColors.DIVIDER)
    }

    private fun actionRow(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ui.dp(NourishSpacing.XS), 0, 0)
    }

    private fun styleDialogActions(dialog: AlertDialog, destructive: Boolean = false) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(if (destructive) NourishColors.CORAL else NourishColors.GREEN)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.BOLD)
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(NourishColors.INK_SECONDARY)
            typeface = Typeface.create(NourishTypography.FAMILY_MEDIUM, Typeface.NORMAL)
        }
    }

    private fun weightedActionParams(last: Boolean = false): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ui.dp(42), 1f).apply {
            if (!last) rightMargin = ui.dp(NourishSpacing.XS)
        }

    private fun matchParams(height: Int, topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.dp(height)).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun matchWrapParams(topMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            this.topMargin = ui.dp(topMargin)
        }

    private fun wrapWrapParams(startMargin: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = ui.dp(startMargin)
        }
}
