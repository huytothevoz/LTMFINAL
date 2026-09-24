# Network Programming Final Project — TCP & UDP Client-Server Applications

Final report project for the **Basic Network Programming** course, Faculty of Information Technology, Ton Duc Thang University.

**Instructor:** Dr. Bui Quy Anh
**Authors:** Vo Trinh Quoc Huy (52400273) · Dao Tuan Minh (52400289)
**Class:** 502065 — Academic year 2025–2026

## Overview

This project builds a suite of network applications that let computers exchange data over a **Client–Server** architecture, using both **TCP** and **UDP** as the underlying transport protocols. The goal is to compare the two protocols in practice — reliability and ordering (TCP) versus speed and low latency (UDP) — while implementing a range of real, usable applications on top of each one. Everything is written in **Java**, using the standard `Socket` / `ServerSocket` (TCP) and `DatagramSocket` (UDP) APIs, with a **thread-per-client** model so the server can handle multiple users concurrently.

The system is entered through a single main menu, which routes the user to the TCP suite or the UDP suite.

## TCP Suite (reliability-first)

Built on top of TCP's guaranteed delivery and ordering.

- **Multi-user Chat Application**
  - User registration/login (server keeps an in-memory + file-backed account store)
  - Broadcast chat, private (1-to-1) messages, group chat, friend requests
  - Custom text protocol with prefixes such as `[ALL]`, `[PM]`, `[GROUP]`, `[FILE]`, `[SYS]`
  - Server maintains a `username → ClientHandler` routing table for direct message delivery
  - Clean disconnect handling via a `finally` cleanup block on every client thread
- **File Transfer** between two machines (upload/download), reading/writing in 8192-byte chunks, with the server validating the received size against the expected file size to avoid corrupted files
- **Mini Apps**, each with its own small custom protocol (`COMMAND|DATA1|DATA2|...`):
  - Remote calculator (parses and evaluates expressions like `2+3*5`)
  - Equation solver
  - English–Vietnamese dictionary lookup (key–value store)
  - System information viewer
  - Quiz app (stateful — server tracks score and question progress across multiple exchanges, and can persist client-submitted questions)

### TCP request patterns covered

| Pattern | Example |
|---|---|
| Stateless request/response | System info service |
| Stateful, multi-round exchange | Quiz app |
| Free-form expression parsing | Calculator |
| Simple key lookup | Dictionary |
| Client writes data to server | Quiz question submission |

## UDP Suite (speed / real-time first)

Built on top of UDP's connectionless model, redesigned to mirror the TCP feature set but optimized for low latency.

- **Chat Application** — same broadcast / private / group messaging concepts as the TCP version, but every request is a self-contained datagram; the server maps `username ⇄ InetSocketAddress` instead of holding open sockets
- **File Transfer with reliability add-ons** — since UDP does not guarantee delivery, the app layers a lightweight reliability mechanism on top:
  - Files are split into fixed-size chunks (`PACKET_SIZE = 8192`)
  - **Stop-and-Wait + ACK + Retry**: sender waits for an acknowledgement after each chunk and retransmits on timeout
  - Separate **control channel** (commands) and **data channel** (a dedicated port issued per transfer), similar in spirit to FTP
- **Real-time collaborative apps**, all using a join → broadcast → sync loop:
  - **Online Chess** — clients send `JOIN`/`MOVE`, server relays moves between the two players
  - **Shared Whiteboard** — draw/undo/clear actions are stored as history on the server and broadcast to all connected clients, including full history replay for new joiners
  - **Shared Notepad** — any client's edit is broadcast to keep all clients' text in sync

## Architecture at a glance

```
                 ┌───────────────┐
                 │   Main Menu   │
                 └──────┬────────┘
           ┌────────────┴────────────┐
           ▼                         ▼
   ┌───────────────┐         ┌───────────────┐
   │   TCP Suite    │         │   UDP Suite    │
   ├───────────────┤         ├───────────────┤
   │ Chat + Auth    │         │ Chat + Auth    │
   │ File Transfer  │         │ File Transfer  │
   │ Mini Apps      │         │  (ACK + Retry) │
   │ (Calc/Equation/│         │ Chess          │
   │  Dictionary/   │         │ Whiteboard     │
   │  SysInfo/Quiz) │         │ Shared Notepad │
   └───────────────┘         └───────────────┘
```

Common design elements across both suites:
- **Thread-per-client** on the server (`accept()`/`receive()` loop hands each client off to its own thread so the server never blocks)
- A custom, human-readable text protocol with a command prefix to route each request to the right handler
- Server acts purely as a **relay/router** for chat and file data — it does not decode or persist file contents, only forwards them
- Clear separation between the **Client** (sends requests, renders UI) and the **Server** (parses commands, coordinates state, routes messages)

## Key takeaways from the project

- **TCP** guarantees ordered, lossless delivery via its 3-way handshake and acknowledgement/retransmission mechanism, at the cost of higher latency — a good fit for chat, file transfer, and any exchange where data integrity matters more than speed.
- **UDP** has no built-in reliability or ordering, which makes it faster and better suited to real-time interaction (chess moves, whiteboard strokes, live text sync), but any reliability needed (e.g. for file transfer) has to be implemented manually with ACKs, timeouts, and retries.
- Building the same set of features twice — once per protocol — made the practical trade-offs between TCP and UDP concrete rather than purely theoretical.

## Known limitations / future work

- UI is functional but not fully polished
- No strong encryption or advanced authentication yet (planned: RSA/AES)
- Not load-tested at scale
- Only tested over a local network; deployment over the public Internet is a future goal

## Tech stack

- **Language:** Java
- **Networking:** `java.net.Socket`, `java.net.ServerSocket` (TCP), `java.net.DatagramSocket`, `java.net.DatagramPacket` (UDP)
- **Concurrency:** one thread per connected client
