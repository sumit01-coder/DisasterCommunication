# Changelog

All notable changes to the Disaster Communication App will be documented in this file.

## [v3.0.0] - 2026-01-19
### Live Location Enhancements
- **Live Status HUD**: Added a floating status card on the map for sharers ("You are Live" + Timer + Stop Button).
- **Self-Pulse Animation**: Added a radar-like pulsing animation for the user's own marker when sharing.
- **Smart Notifications**:
  - **Chat Broadcast**: Automatically sends a "Started sharing..." message to the group chat.
  - **System Alert**: Peers receive a high-priority system notification to track the sharer with one tap.
- **Reliability**: Confirmed "Share All" broadcasts to all connected peers by default.
- **Refactoring**: Cleaned up `MapFragment` and `LiveLocationService` for better structure and performance.

### Peer Tracking
- **Auto-Center**: Tapping a notification centers the map on the sharer.
- **Real-Time Visuals**: Improved marker updates and visual feedback for active peers.

---
## [v2.7.6] - 2026-01-18
- Initial Live Location Sharing implementation.
- Basic background service for location updates.
- Peer tracking logic.
