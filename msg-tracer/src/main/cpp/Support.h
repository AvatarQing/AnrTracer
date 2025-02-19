#ifndef MSGTRACER_SUPPORT_H
#define MSGTRACER_SUPPORT_H

#include <stdint.h>
#include <sys/types.h>
#include <errno.h>
#include <assert.h>
#include <type_traits>
#include <ctime>
#include <string_view>

namespace MsgTracer {

    class ScopedFileDescriptor {
    public:
        ScopedFileDescriptor() noexcept: mFD(-1) {}

        explicit ScopedFileDescriptor(int fd) : mFD(fd) {}

        ~ScopedFileDescriptor() {
            reset();
        }

        ScopedFileDescriptor(ScopedFileDescriptor &&other) noexcept: mFD(other.release()) {}

        ScopedFileDescriptor &operator=(ScopedFileDescriptor &&other) noexcept {
            reset(other.release());
            return *this;
        }

        int get() const { return mFD; }

        bool valid() const { return mFD != -1; }

        void reset(int fd = -1) {
            if (mFD != fd) {
                if (mFD != -1)
                    close_(mFD);
                mFD = fd;
            }
        }

        int release() __attribute__((warn_unused_result)) {
            int fd = mFD;
            mFD = -1;
            return fd;
        }

    private:
        int mFD;

        static void close_(int fd);

        ScopedFileDescriptor(const ScopedFileDescriptor &) = delete;

        ScopedFileDescriptor &operator=(const ScopedFileDescriptor &) = delete;
    };

    template<typename Func, typename... Args>
    inline static auto handleInterrupt(Func func, Args... args) -> decltype(func(args...)) {
        decltype(func(args...)) ret;
        do {
            ret = func(args...);
        } while (ret == -1 && errno == EINTR);
        return ret;
    }

/**
 * A class for reading a file, line by line, without using fopen/fgets or other
 * functions which may allocate memory.
 */
    class LineReader {
    public:
        explicit LineReader(int fd)
                : mFD(fd),
                  mHitEof(false),
                  mBufUsed(0) {}

        /// The maximum length of a line.
        static constexpr size_t kMaxLineLen = 512;

        /**
         * Return the next line from the file.
         *
         * @param line (output) a pointer to the start of the line. The line is NUL terminated.
         * @param len (output) the length of the line (not inc the NUL byte).
         * @return true if successful (false on EOF).
         * @note One must call popLine() after this function, otherwise you'll continue to
         *       get the same line over and over.
         */
        bool getNextLine(const char **line, size_t *len);

        void popLine(size_t len);

    private:
        const int mFD;
        bool mHitEof;
        unsigned mBufUsed;
        char mBuf[kMaxLineLen];
    };

/**
 * A class for enumerating a directory without using diropen/readdir or other functions
 * which may allocate memory.
 */
    class DirectoryReader {
    public:
        explicit DirectoryReader(int fd) : mFD(fd), mHitEof(false), mBufUsed(0) {}

        /**
         * Return the next entry from the directory.
         *
         * @param name (output) the NUL terminated entry name.
         * @return true if successful (false on EOF).
         * @note After calling this, one must call popEntry() otherwise you'll get the same
         *       entry over and over.
         */
        bool getNextEntry(const char **name);

        void popEntry();

    private:
        static constexpr size_t kDirent64StructSize = 24 + 256;

        const int mFD;
        bool mHitEof;
        unsigned mBufUsed;
        uint8_t mBuf[kDirent64StructSize + NAME_MAX + 1];
    };

    namespace Support {

        size_t utf16To8(const uint16_t *src, size_t srcLen, char *dst, size_t dstLen);

        size_t strlen(const char *s);

        size_t strlcpy(char *s1, const char *s2, size_t len);

        int strncmp(const char *a, const char *b, size_t len);

// Return the length of the given unsigned integer when expressed in base 10.
        unsigned uintLen(uintmax_t i);

        unsigned hexLen(uintmax_t i);

// Convert an unsigned integer to a string
//   output: (output) the resulting string is written here. This buffer must be
//     large enough to hold the resulting string. Call |my_uint_len| to get the
//     required length.
//   i: the unsigned integer to serialise.
//   i_len: the length of the integer in base 10 (see |my_uint_len|).
        void uitos(char *output, uintmax_t i, unsigned i_len);

// Read a hex value
//   result: (output) the resulting value
//   s: a string
// Returns a pointer to the first invalid character.
        uint64_t readHex(const char *&buf, size_t &bufSize);

        uint64_t readUInt(const char *&buf, size_t &bufSize);

        static inline unsigned atou(const char *s) {
            size_t l = strlen(s);
            return readUInt(s, l);
        }

// Same as localtime_r(3), but rely on timezone info in 'tm'.
        std::tm *localtimeWithoutTimezone(const time_t *t, std::tm *tm);

        ssize_t robustRead(const ScopedFileDescriptor &fd, void *buffer, size_t size);

        int robustWrite(const ScopedFileDescriptor &fd, const void *buffer, size_t size);

        ssize_t readFileAsString(const char *filePath, char *buffer, size_t bufSize);

        static constexpr bool stringStartsWith(std::string_view s, std::string_view prefix) {
            return s.substr(0, prefix.size()) == prefix;
        }

        static constexpr bool stringStartsWith(std::string_view s, char prefix) {
            return !s.empty() && s.front() == prefix;
        }

        static constexpr bool stringEndsWith(std::string_view s, std::string_view suffix) {
            return s.size() >= suffix.size() &&
                   s.substr(s.size() - suffix.size(), suffix.size()) == suffix;
        }

        static constexpr bool stringEndsWith(std::string_view s, char prefix) {
            return !s.empty() && s.back() == prefix;
        }

        extern const size_t pageSize;
    }   // namespace Support

    class StringBuilder {
        char *const mBuffer;
        const unsigned mCapacity;
        unsigned mSize;

    public:
        StringBuilder(char *buffer, unsigned capacity) :
                mBuffer(buffer), mCapacity(capacity), mSize(0) {}

        const char *c_str() const { return mBuffer; }

        unsigned size() const { return mSize; }

        unsigned spaceLeft() const { return mCapacity - mSize; }

        operator std::string_view() const noexcept { return std::string_view(mBuffer, mSize); };

        char *writeBuffer() { return mBuffer + mSize; }

        StringBuilder &advance(size_t size) {
            mSize += size;
            assert(mSize < mCapacity);
            return *this;
        }

        StringBuilder &unwind(size_t size) {
            assert(mSize >= size);
            mSize -= size;
            return *this;
        }

        StringBuilder &reset() {
            mSize = 0;
            return *this;
        }

        StringBuilder &operator<<(const char *str) {
            size_t left = spaceLeft();
            size_t ret = Support::strlcpy(writeBuffer(), str, left);
            return advance(ret >= left ? left - 1 : ret);
        }

        StringBuilder &operator<<(char c) {
            if (mCapacity - mSize > 1) {
                mBuffer[mSize++] = c;
            }
            return *this;
        }
    };
}

#endif