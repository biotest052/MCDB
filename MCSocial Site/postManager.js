async function fetchAllPosts() {
    try {
        const req = await fetch("http://localhost:8000/query", {
            method: "POST",
            headers: {
                'Authorization': getAuthToken()
            },
            body: "select * from posts"
        })

        if (!req.ok) {
            alert("Request failed: " + req.statusText);
        } else {
            const res = await req.json();
            return res;
        }

    } catch (error) {
        alert(error);
    }
}

async function mapPosts() {
    const container = document.getElementById("posts");

    const res = await fetchAllPosts();

    const posts = res.reverse();

    let ret = ``;

    for (let i = 0; i < posts.length; i++) {
        const toAppend = await generatePost(posts[i]);
        ret += toAppend;
        if (i !== posts.length - 1) ret += "<br/><br/>"
    }

    if (posts.length === 0) {
        ret = `<p style="text-align: center;">Nothing to show here!</p>`
    }

    container.innerHTML = ret;

}

async function mapPostsUserId() {
    const urlParams = new URLSearchParams(window.location.search);
    const id = urlParams.get('id');

    const req = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `select * from posts where userId=${id}`
    })

    if (!req.ok) {
        return null;
    } else {
        const res = await req.json();

        const posts = res.reverse();

        if (posts.length > 0) {
            const container = document.getElementById("posts");
            let ret = ``;

            for (let i = 0; i < posts.length; i++) {
                const toAppend = await generatePost(posts[i]);
                ret += toAppend;
                if (i !== posts.length - 1) ret += "<br/><br/>"
            }

            container.innerHTML = ret;

        } else container.innerHTML = `<p style="text-align: center;">No posts from this user!</p>`;

    }

}

async function displaySinglePost() {
    const urlParams = new URLSearchParams(window.location.search);
    const id = urlParams.get('id');
    const req = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `select ${id} from posts`
    })

    if (!req.ok) {
        return null;
    } else {
        const res = await req.json();

        if (res !== undefined && res !== null) {
            const container = document.getElementById("posts");
            const post = await generatePost(res);
            container.innerHTML = post;
        } else return null;

    }

}

async function deletePost(id) {
    const ref = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `select ${id} from posts`
    });

    if (!ref.ok) {
        alert(await ref.text());
    } else {
        const refObj = await ref.json();

        if (refObj.fileId !== null && refObj.fileId !== undefined) {
            const loadingMessage = document.getElementById(`loadingMessage${id}`);
            if (loadingMessage !== null)
                loadingMessage.innerHTML = `Deleting associated file, please wait...`;
            const delReq = await fetch(`http://localhost:8000/deleteFile?q=${refObj.fileId}`)

            if (!delReq.ok) {
                const reason = await delReq.text();
                console.log("Failed to delete file: ", reason);
            }

            if (loadingMessage !== null)
                loadingMessage.innerHTML = `Deleting associated file, please wait...`;

        }

        const req = await fetch("http://localhost:8000/query", {
            method: "POST",
            headers: {
                'Authorization': getAuthToken()
            },
            body: `delete ${id} from posts`
        })

        if (!req.ok) {
            alert(await req.text());
        } else {
            redirect("index.html");
        }
    }

}