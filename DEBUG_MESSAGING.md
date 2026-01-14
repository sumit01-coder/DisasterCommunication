# Debugging Messaging - Quick Guide

## Enable Debug Logging

Debug logging is automatically enabled. Check Android Studio Logcat with filter: **`MessageDebug`**

## Key Log Tags to Monitor

| Tag | Purpose |
|-----|---------|
| `MessageDebug` | All debug helper logs |
| `PacketHandler` | Message sending/receiving |
| `ChatFragment` | Chat UI interactions |
| `DisasterApp` | Main activity message routing |

## Common Debug Scenarios

### 1. **Message Not Sending**

**Filter**: `MessageDebug` or `PacketHandler`

**Look for**:
```
📤 MESSAGE SENT
   ID: xxx
   To: [recipient_id or "ALL"]
   Content: [message text]
```

**If missing**: Check network connectivity logs
**If present**: Message was sent, check receiver side

---

### 2. **Message Not Received**

**Filter**: `MessageDebug`

**Look for**:
```
📥 MESSAGE RECEIVED
   ID: xxx
   From: [sender_id]
   To: [your_id or "ALL"]
   Content: [message text]
```

**If missing**: Network/transport issue
**If present but not displayed**: Check routing logs

---

### 3. **Private Message Not Displayed**

**Filter**: `MessageDebug`

**Look for**:
```
🔀 MESSAGE ROUTING
   Message ID: xxx
   Current Chat: [chat_id]
   Message Partner: [partner_id]
   Will Display: ✅/❌
```

**If "Will Display: ❌"**: Message went to notification instead (correct if chat not open)
**If "Will Display: ✅" but still not showing**: UI issue, check ChatFragment logs

---

### 4. **Unread Count Not Updating**

**Filter**: `MessageDebug`

**Look for**:
```
🔢 UNREAD COUNT UPDATE
   Member: [member_id]
   New Count: X
   Reason: [why count changed]
```

**If count updates but UI doesn't**: Member list refresh issue
**If count doesn't update**: Message routing problem

---

### 5. **Message Preview Not Showing**

**Filter**: `MessageDebug`

**Look for**:
```
💬 MESSAGE PREVIEW UPDATE
   Member: [member_id]
   Preview: [message text]
```

**Check**: Member list adapter binding

---

### 6. **Connection Issues**

**Filter**: `MessageDebug`

**Look for**:
```
🔵 DEVICE CONNECTED / ⚫ DEVICE DISCONNECTED
   ID: xxx
   Name: xxx
   Type: Bluetooth/Mesh
```

---

## Test Sequence

1. **Send global message**: Should see "📤 MESSAGE SENT ... To: ALL"
2. **Receive global message**: Should see "📥 MESSAGE RECEIVED"
3. **Send private message**: Should see "📤 MESSAGE SENT ... To: [specific_id]"
4. **Receive private (chat open)**: Should see "🔀... Will Display: ✅"
5. **Receive private (chat closed)**: Should see "🔀... Will Display: ❌" + "🔢 UNREAD COUNT UPDATE"

---

## Quick Logcat Filters

### All Messages
```
tag:MessageDebug | tag:PacketHandler | tag:ChatFragment
```

### Only Sending
```
tag:MessageDebug 📤
```

### Only Receiving
```
tag:MessageDebug 📥
```

### Only Routing Issues  
```
tag:MessageDebug 🔀
```

### Errors Only
```
tag:MessageDebug ❌
```

---

## Common Issues & Solutions

| Symptom | Possible Cause | Check |
|---------|----------------|-------|
| No "📤 SENT" log | App crash / UI not initialized | Check ChatFragment created |
| "📤 SENT" but no "📥 RECEIVED" | Network issue | Check connection logs 🔵/⚫ |
| "📥 RECEIVED" but not displayed | Routing issue | Check 🔀 ROUTING logs |
| Unread count stuck at 0 | Counter not incrementing | Check 🔢 UNREAD logs |
| Unread count stuck at X | Counter not resetting | Check openPrivateChat() call |
| Preview not updating | Member list not refreshing | Check 💬 PREVIEW logs |

---

## Disable Debug Logging

Add to `MainActivityNew.onCreate()`:
```java
MessageDebugHelper.setDebugEnabled(false);
```
