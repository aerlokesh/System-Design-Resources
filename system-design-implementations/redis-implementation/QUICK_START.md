# Redis All-in-One Quick Start Guide

## 🚀 Quick Start

### Option 1: All-in-One Interactive Program (RECOMMENDED)
The easiest way to explore all Redis data structures in one place!

```bash
python redis_all_in_one.py
```

**Features:**
- ✅ Interactive menu to choose which data structure to explore
- ✅ All 5 Redis data structures in one file
- ✅ Real-world use cases for each structure
- ✅ Easy to run and learn

**Menu Options:**
1. **STRINGS** - Basic key-value, counters, caching, distributed locks
2. **LISTS** - Task queues, activity feeds, message queues
3. **SETS** - Unique tracking, tags, social networks
4. **HASHES** - User profiles, sessions, shopping carts
5. **SORTED SETS** - Leaderboards, priority queues, time series
0. **RUN ALL** - Execute all examples at once
q. **QUIT** - Exit program

### Option 2: Individual Files
Run specific data structure examples:

```bash
# Strings
python 01-basics/01_strings.py

# Lists
python 01-basics/02_lists.py

# Sets
python 01-basics/03_sets.py

# Hashes
python 01-basics/04_hashes.py

# Sorted Sets
python 01-basics/05_sorted_sets.py
```

## 📋 Prerequisites

1. **Redis Server Running:**
   ```bash
   # Using Docker (recommended)
   docker run -d --name redis-learning -p 6379:6379 redis:latest
   
   # Or locally installed Redis
   redis-server
   ```

2. **Python Dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

## 🎯 What You'll Learn

### 1. STRINGS
- Basic key-value operations (SET, GET)
- Counter operations (INCR, DECR)
- Session management
- Rate limiting
- Caching API responses
- Distributed locks

### 2. LISTS
- Queue operations (LPUSH, RPUSH, LPOP, RPOP)
- Task queue (Producer-Consumer pattern)
- Activity feeds
- Reliable queue with backup
- Undo/redo stack

### 3. SETS
- Unique collections (SADD, SREM)
- Set operations (Union, Intersection, Difference)
- Unique visitor tracking
- Tagging systems
- Social network relationships
- Lottery/raffle systems

### 4. HASHES
- Object storage (HSET, HGET)
- User profiles
- Session storage
- Shopping carts
- Feature flags
- Product inventory

### 5. SORTED SETS
- Scored collections (ZADD, ZRANGE)
- Gaming leaderboards
- Priority queues
- Time series data
- Rate limiting (sliding window)
- Trending topics

## 💡 Usage Examples

### Quick Test (All-in-One)
```bash
# Start the interactive program
python redis_all_in_one.py

# At the menu, type:
# 1 - To see STRINGS examples
# 2 - To see LISTS examples
# 3 - To see SETS examples
# 4 - To see HASHES examples
# 5 - To see SORTED SETS examples
# 0 - To run ALL examples
# q - To quit
```

### Quick Test (Individual Files)
```bash
# Test strings
python 01-basics/01_strings.py

# Test all basics
python 01-basics/*.py
```

## 🔧 Troubleshooting

**Connection Error:**
```
✗ ERROR: Could not connect to Redis
```
**Solution:** Make sure Redis is running:
```bash
docker ps | grep redis  # Check if container is running
docker start redis-learning  # Start if stopped
```

**Import Error:**
```
ModuleNotFoundError: No module named 'redis'
```
**Solution:** Install dependencies:
```bash
pip install redis
```

## 📚 Next Steps

1. ✅ Run `redis_all_in_one.py` to explore all data structures
2. ✅ Read `INTERVIEW_GUIDE.md` for system design interview tips
3. ✅ Check `HOW_TO_RUN.md` for detailed setup instructions
4. ✅ Explore individual files in `01-basics/` for more examples

## 🎓 Interview Preparation

This all-in-one file is perfect for:
- Learning Redis data structures quickly
- Practicing before system design interviews
- Understanding real-world use cases
- Testing different Redis operations

**Pro Tip:** Run option `0` (RUN ALL) to see all examples at once, then explore individual data structures for deeper understanding!

## 📞 Need Help?

- Check `HOW_TO_RUN.md` for detailed setup
- See `INTERVIEW_GUIDE.md` for interview tips
- Review individual files in `01-basics/` for more details
