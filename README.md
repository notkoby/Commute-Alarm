# Commute-Alarm
An automatically adjusting alarm based on live traffic data to give you more time to sleep when there's less traffic

This is a proof-of-concept app based on a post from @BenLouisX on X/Twitter. His post can be found [here](https://x.com/BenLouisX/status/2119810617699664123) 
This app is just proof-of-concept and was made primarily with AI. Do not expect it to be updated often, and expect possible bugs.

### Here's how Commute Alarm works:
#### Commute Alarm gets real-time traffic data to adjust your alarm
- Commute Alarm checks every hour, and twenty minutes before your alarm is set to go off, for the live traffic data to your work destination, and adjusts your alarm accordingly. Light traffic? You get more time to sleep! Heavy traffic? Don't worry, you'll be woken up early so you don't get to work late.
- Commute Alarm uses a free-tier TomTom API to get traffic data. Users are limited to 5 requests per hour, after which the app can use open source maps (OSRM) to get route data, though it will not have real-time traffic data. You will not run out of API requests unless you're constantly refreshing the traffic data or changing your work location. You can also add your own TomTom or Google Maps API key for unlimited requests.

#### Commute Alarm also gives your time to get ready
- You can adjust how much time you need to get ready in the morning, and your alarm will be adjusted accordingly.
- You can choose to have the alarm go off at a specific time if it cannot fetch traffic data, so you don't wake up late by accident.

#### Disclaimers:
Since this app is just proof-of-concept, there may be major bugs anytime. I will try to fix issues that are reported, but until they're reported I likely have no idea they exist. To ensure you don't get into any bad situations, please follow these recommendations:
- Be careful with any API key you put into the app. There is no feature yet to stop you from going over your limit, and you may go over your limit whether by accident or by a bug in the app. I would recommend limiting any API key you put into the app to ensure you're not automatically charged if you go over the limit. We are not responsible if that happens.
- There may be bugs that cause the alarm to not go off, or to go off at an incorrect time. I recommend setting a backup alarm with your phone's alarm clock in case of a bug. We are not responsible for any bugs that cause issues with the alarm or its timing.
- The default API key included in the app is rate-limited at 5 requests per hour, per person. However, since it's using my own API key, which was free, it may stop at any point if it reaches the rate limit or is automatically or manually stopped. If this happens, the app will attempt to use open source map data to ensure your alarm goes off on time, if that is enabled in settings. However, if this does not happen, or any similar issues happen, we are not responsible.
In general, please know that I hope this app works properly and as intended all the time, however it might not and we bear no responsibilities for any issues that occur. Please help out by submitting an issue if there is a bug though!
