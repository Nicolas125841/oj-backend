# BeavesOJ Backend

Run `docker compose up -d` to start the server, will setup Postgres, Server, Filestore, and (soon) nginx.

IMPORTANT: The container/machine that runs the runwrapper program must use a v6 linux kernel to properly support cgroupsv2.
Also, do not let systemd get access to cgroup file system in base machine

TODO: Setup bwrap for safe Kotlin, Java, and Python execution (can be used for any non compiled language later too)
