#define _GNU_SOURCE

#include <errno.h>
#include <error.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/resource.h>
#include <sys/stat.h>
#include <sys/wait.h>

//NOTE: Install bwrap on docker and create user for NOBODY
/*
Example: sudo ./runwrap.o 20000 4000 bwrap --ro-bind /usr /usr --ro-bind . /subject --symlink usr/lib /lib --symlink usr/lib64 /lib64 --chdir /subject --unshare-all --cap-drop ALL --die-with-parent --new-session ./a.out
*/

#define TIMEKILLED 152
#define OOMKILLED 137
#define NOBODY 1000
#define OJGROUP "/sys/fs/cgroup/oj"
#define MEMMAX OJGROUP "/memory.max"
#define MEMPEAK OJGROUP "/memory.peak"
#define MEMSMAX OJGROUP "/memory.swap.max"
#define MEMZSMAX OJGROUP "/memory.zswap.max"
#define ATTACH OJGROUP "/cgroup.procs"
#define SAFE(cond) if((cond)) { error(SERVER_ERROR, errno, ""); }

enum RESULT {SUCCESS, SERVER_ERROR, COMPILE_ERROR, RUNTIME_ERROR, TIMEOUT, WRONG_ANSWER, MEMORY_EXC};

void attach_cgroup(const long ml) {
    FILE *fp;

    SAFE(mkdir(OJGROUP, S_IRWXU));

    SAFE((fp = fopen(MEMMAX, "w")) == NULL);
    SAFE(fprintf(fp, "%ld", ml * 1000000l) < 0);
    SAFE(fclose(fp))

    SAFE((fp = fopen(MEMSMAX, "w")) == NULL)
    SAFE(fprintf(fp, "0") < 0)
    SAFE(fclose(fp))

    SAFE((fp = fopen(ATTACH, "w")) == NULL)
    SAFE(fprintf(fp, "%d", getpid()) < 0)
    SAFE(fclose(fp))
}

//Sets the time and memory limits (and other protections) on test process
void set_limits(const long tl, const long ml) {
    struct rlimit new;

    //Set time limit with one second buffer to catch SIGXCPU
    new.rlim_cur = (tl + 999l)/1000l;
    new.rlim_max = (tl + 999l)/1000l + 1l;
    SAFE(setrlimit(RLIMIT_CPU, &new));

    //Unlimit memory because cgroups does that
    new.rlim_cur = RLIM_INFINITY;
    new.rlim_max = RLIM_INFINITY;
    SAFE(setrlimit(RLIMIT_AS, &new));
    SAFE(setrlimit(RLIMIT_DATA, &new));
    SAFE(setrlimit(RLIMIT_STACK, &new));

    //Disable core dumping
    new.rlim_cur = 0l;
    new.rlim_max = 0l;
    SAFE(setrlimit(RLIMIT_CORE, &new));

    //Set memory limits with cgroups
    attach_cgroup(ml);
}

void record_cgroup(long* peak) {
    FILE *fp;

    SAFE((fp = fopen(MEMPEAK, "r")) == NULL);
    SAFE(fscanf(fp, "%ld", peak) != 1);
    SAFE(fclose(fp));
}

int remove_cgroup() {
    for(int i = 0; i < 1000; i++) {
        if(!rmdir(OJGROUP)) {
            return 0;
        }

        usleep(1000);
    }

    return 1;
}

int main(int argc, char* argv[]) {
    if(argc < 4) {
        fprintf(stderr, "Usage: runwrap [time in ms] [memory in kb] [cmd + args]\n");
        return SERVER_ERROR;
    }

    SAFE(remove_cgroup() && errno != ENOENT);

    long tl = atol(argv[1]);
    long ml = atol(argv[2]);
    char** cmd = &argv[3];
    pid_t c_pid = fork();

    if(c_pid == -1) {
        error(SERVER_ERROR, errno, "");
    } else if(c_pid) {
        struct rusage metrics;
        long memory;
        int status;

        SAFE(wait4(c_pid, &status, 0, &metrics) == -1);

        record_cgroup(&memory);

        long time = metrics.ru_utime.tv_usec + 1000000ul * metrics.ru_utime.tv_sec + metrics.ru_stime.tv_usec + 1000000ul * metrics.ru_stime.tv_sec;

        //Set metadata for later parsing
        fprintf(stderr, "%ld\n%ld\n", memory, time);

        int verdict = SERVER_ERROR;

        if(WIFEXITED(status)) {
            switch(WEXITSTATUS(status)) {
                case 0: verdict = SUCCESS;
                break;
                case OOMKILLED: verdict = MEMORY_EXC;
                break;
                case TIMEKILLED: verdict = TIMEOUT;
                break;
                default: verdict = RUNTIME_ERROR;
                break;
            }
        }

        SAFE(remove_cgroup());

        return verdict;
    } else {
        //Set limits & cgroup
        set_limits(tl, ml);

        if (getuid() == 0) {
            SAFE(setgid(NOBODY) != 0);
            SAFE(setuid(NOBODY) != 0);
        }

        //Make sure we drop permissions
        SAFE(getuid() == 0 || getgid() == 0 || geteuid() == 0);

        //Start program
        SAFE(execvpe(argv[3], &argv[3], &argv[argc]));
    }

    //Should never get here
    return SERVER_ERROR;
}