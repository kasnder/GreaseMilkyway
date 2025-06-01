# GreaseMilkyway

<a href='https://github.com/kasnder/GreaseMilkyway/releases/latest'><img height=70 alt='Get it on Github' src='https://raw.githubusercontent.com/TrackerControl/tracker-control-android/master/images/get-it-on-github.png'/></a>
<a href='https://play.google.com/store/apps/details?id=net.kollnig.greasemilkyway'><img height=70 alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png'/></a>

GreaseMilkyway is an Android accessibility service designed to help people with attention-related conditions (such as ADHD) manage their digital environment. By allowing users to block distracting content in apps, it helps create a more focused and less overwhelming digital experience.

It is a successor to the [GreaseWorld initiative](https://greaseuniverse.github.io) that previously developed a range of tooling to respond to potentially harmful patterns in mobile apps.

## Purpose

This app is specifically designed as an accessibility tool to help people who:
- Struggle with attention regulation
- Find certain app features overly stimulating or distracting
- Need help maintaining focus while using their devices
- Want to customise their digital environment to better suit their needs

GreaseMilkyway is designed to make digital spaces more accessible and manageable for people with attention-related conditions.

## Adding new Rules

Rules follow a simple syntax with key-value pairs separated by `##`:

```
<package-name>##viewId=<view-id>##desc=<pipe-separated-list>##color=<hex-colour>
```

### Components:

- `package-name`: The package name of the target app (e.g., `com.example.app`)
- `viewId`: (Optional) The resource ID of the view to block
- `desc`: (Optional) Pipe-separated list of content descriptions to match
- `color`: (Optional) Hex colour for the overlay (defaults to white #FFFFFF)

### Examples:

```
# Block YouTube recommendations
com.google.android.youtube##viewId=com.google.android.youtube:id/watch_list##desc=Shorts|Go to channel##colour=FFFFFF##comment=Hide next-up video recommendations

# Block WhatsApp AI button
com.whatsapp##viewId=com.whatsapp:id/fab_second##colour=FFFFFF##blockTouches=true##comment=Hide AI button

# Block Instagram Stories
com.instagram.android##desc=reels tray container##colour=FFFFFF##blockTouches=true##comment=Hide Stories

# Example of a rule that allows touches to pass through
com.example.app##viewId=com.example.app:id/some_view##colour=FFFFFF##blockTouches=false##comment=Hide but allow interaction
```

## Creating Your Own Rules

To create effective rules, you'll need to identify the elements you want to block.  Several apps on the Play Store can help you inspect layouts directly on your device, e.g. **Developer Assistant** ([Play Store Link](https://play.google.com/store/apps/details?id=com.appsisle.developerassistant)).

### Tips for Creating Rules

1. **View IDs**: Look for unique identifiers in the layout hierarchy. They usually follow the pattern `package.name:id/identifier`
2. **Content Descriptions**: Elements often have content descriptions that can be used for matching
3. **Touch Blocking**: Use `blockTouches=true` to prevent interaction with blocked elements, or `blockTouches=false` to allow touches to pass through
4. **Testing**: After creating a rule:
   - Test it thoroughly
   - Make sure it doesn't block unintended elements
   - Verify it works across different app versions

### Common Patterns

- For social media feeds: Look for recycler views or list views
- For buttons: Check for FAB (Floating Action Button) IDs or specific button identifiers
- For ads: Often have "ad" or "sponsored" in their IDs or descriptions

## Privacy

GreaseMilkyway:
- Runs entirely on your device
- Doesn't collect any data
- Doesn't require internet access
- Only uses the accessibility service to analyse and block content

## Contributing

Contributions are welcome! Feel free to submit issues and pull requests.

### Default Rules

The app comes with a set of default rules to help users get started. These rules are stored in [`app/src/main/assets/distraction_rules.txt`](app/src/main/assets/distraction_rules.txt). We welcome contributions to improve these rules! If you've found a way to block distracting content in an app, please consider:

1. Testing your rule thoroughly
2. Adding a clear comment explaining what the rule blocks
3. Submitting a pull request with your addition

Each rule should follow the format described in the [Rule Format](#rule-format) section above.

## License

This project is licensed under the GNU General Public License v3.0 - see the LICENSE file for details. 
