with open("app/src/main/java/com/newsthread/app/util/NotificationHelper.kt", "r") as f:
    content = f.read()

import re

old_code = """        } else {
            // App is background, show system notification
            with(NotificationManagerCompat.from(context)) {
                notify(storyId.hashCode(), builder.build())
            }
        }"""

new_code = """        } else {
            // App is background, show system notification
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    with(NotificationManagerCompat.from(context)) {
                        notify(storyId.hashCode(), builder.build())
                    }
                }
            } else {
                with(NotificationManagerCompat.from(context)) {
                    notify(storyId.hashCode(), builder.build())
                }
            }
        }"""

content = content.replace(old_code, new_code)

with open("app/src/main/java/com/newsthread/app/util/NotificationHelper.kt", "w") as f:
    f.write(content)
