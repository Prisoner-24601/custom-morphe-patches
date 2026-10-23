# ⌨️ Keyboard Speed Patches

A single YouTube patch for use with [Morphe](https://morphe.software): the `-` and `=` keys of a
hardware keyboard decrease and increase the playback speed, like `<` and `>` on the YouTube website.

This bundle is an add-on. It changes the speed through the official Morphe YouTube patches, so
apply it **together with** the official "Morphe Patches" source, never on its own.

### How to use these patches

1. Add this source to Morphe Manager: https://morphe.software/add-source?github=Prisoner-24601/keyboard-speed-patches
2. Patch YouTube in **Expert mode**, which gives each source its own tab. Keep your usual
   selection in the Morphe Patches tab and enable **Keyboard speed shortcuts** in this one.
3. Confirm the "Multiple patch sources selected" warning.

### Behavior

- `-` / numpad `-` lowers the speed, `=` / `+` / numpad `+` raises it, holding a key repeats.
- Each press moves to the next multiple of the step (default 0.25x, so 1.1x goes to 1.25x
  or 1.0x), between 0.25x and 8x. A toast shows the new speed.
- Only while a video is open. Text fields (search, comments) and Ctrl/Alt/Meta combinations
  are left alone.
- The step is the **Speed step** patch option: 0.05x, 0.10x, 0.25x or 0.50x.

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.0.0](https://github.com/Prisoner-24601/keyboard-speed-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;1 patches total
<details open>
<summary>📦 YouTube&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Keyboard speed shortcuts](#keyboard-speed-shortcuts) | Makes the - and = keys of a hardware keyboard decrease and increase the playback speed. Apply together with the Morphe YouTube patches, which this patch uses to change the speed. | • Speed step |

</details>

<!-- PATCHES_END -->

## 🧑‍💻 Development

- Make changes on the `dev` branch and merge `dev` into `main` for a stable release
  (merge, do not squash).
- Use semantic commit messages: `feat:` and `fix:` create releases, `chore:` does not.
- Do not edit the generated `patches-list.json`, `patches-bundle.json` or `CHANGELOG.md` by hand.

### 🛠️ Building locally

- Building needs a GitHub token with the `read:packages` scope, see
  [the patcher setup docs](https://github.com/MorpheApp/morphe-patcher/blob/main/docs/2_1_setup.md#-prepare-the-environment).
- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`

## 📜 License

These patches are licensed under the [GNU General Public License v3.0](LICENSE).
They are not made or endorsed by the Morphe project.
