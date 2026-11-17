\# Vanish – Class Network Client



Vanish is an Android messaging application developed for the Mobile Application Development course. It allows students to exchange temporary text messages through a shared WebSocket relay. Messages are designed to expire after 30 seconds.



\## Project Information



\- \*\*Platform:\*\* Android

\- \*\*Language:\*\* Kotlin

\- \*\*UI:\*\* Jetpack Compose

\- \*\*Local Database:\*\* Room (SQLite)

\- \*\*Networking:\*\* OkHttp WebSocket

\- \*\*Minimum Android SDK:\*\* 26

\- \*\*Relay URL:\*\* `wss://vanish-relay-sawaira.bonto.page/ws`



\## Main Features



\- Join the class messaging network using a display name.

\- Send and receive temporary text messages.

\- Store messages locally using Room.

\- Use a local outbox to queue messages while offline.

\- Drop queued messages that become older than the 30-second limit.

\- Reject duplicate message IDs.

\- Delete expired message rows from the local database.

\- Reconnect to the relay after connection loss.

\- Use relay timestamps and monotonic elapsed time for countdown calculations after synchronization.



\## Build and Run



1\. Clone or download this repository.

2\. Open the `vanish` project folder in Android Studio.

3\. Allow Gradle to sync.

4\. Connect an Android phone or start an emulator.

5\. Run the application.



\## Message Lifecycle



A message follows this general lifecycle:



`Created → Waiting → Sent → Delivered → Displayed → Expired`



If a message remains queued offline beyond its allowed age, it is dropped instead of being sent.



\## Local Storage and Expiry



The application uses Room with three tables:



\- `messages`: stores received and displayed message content.

\- `outbox`: stores messages waiting to be sent.

\- `seen`: stores message IDs and expiry timestamps for duplicate rejection.



The repository periodically checks expiry and deletes expired message records. The SQLite database is configured with `secure\_delete` enabled and TRUNCATE journal mode.



\## Countdown



The application receives relay server time through the welcome frame. `ClockSync` uses the relay timestamp and Android's monotonic elapsed time to estimate current relay time after synchronization. This reduces the effect of an incorrect phone clock on the countdown.



\## Limitations



Vanish cannot prevent recipients from taking screenshots, copying text, photographing their screens, or remembering message contents. It also cannot guarantee that a message is removed from the relay at the exact moment a recipient's phone is switched off or disconnected.



The application does not claim end-to-end encryption or authenticated private messaging.



\## Project Structure



\- `app/` – Android application source code.

\- `docs/` – Project documentation and testing materials.

\- `relay/` – Local mock relay and test scripts.

\- `RelayClient.kt` – WebSocket connection and reconnection.

\- `ChatRepository.kt` – Message lifecycle, outbox and expiry handling.

\- `ClockSync.kt` – Relay-based time synchronization.

\- `Protocol.kt` – WebSocket message encoding and decoding.

\- `Entities.kt` – Room database entities and DAO.



\## Tools and Libraries



\- Kotlin

\- Android Studio

\- Jetpack Compose

\- Room

\- OkHttp

\- Kotlin Coroutines



\## Academic Integrity and AI Assistance



AI assistance was used during development and documentation preparation. The student is responsible for reviewing, testing, understanding, and explaining the submitted implementation. The actual extent of AI assistance should be described accurately by the student.



\## Repository



GitHub: https://github.com/Faizan2007-1/Vanish-Class-Network



