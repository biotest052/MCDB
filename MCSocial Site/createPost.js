function checkAuth() {
    const userId = getSignedInId();

    if (userId == 0) {
        alert("You must be signed in to create a post!");
        redirect("index.html");
    }

}

async function createPost() {
    const userId = getSignedInId();
    const username = getUsername();

    if (userId == 0) {
        alert("You must be signed in to create a post!");
    } else {
        const postFile = document.getElementById("postFile");
        if (postFile.files.length > 0) {
            const file = postFile.files[0];
            if ((file.size / 1000000) > 10) alert("Files cannot be larger than 10 MB!");
            else {
                const loadingMessage = document.getElementById("loadingMessage");
                loadingMessage.innerHTML = "Uploading file, please wait...<br/><br/>";
                try {
                    const processRes = await processAndUploadFile(file);
                    const uploadRes = await uploadFile(processRes);
                    await submitQuery(username, processRes.mime, uploadRes.link, uploadRes.fileId);
                    loadingMessage.innerHTML = "";
                } catch (error) {
                    alert(error);
                    loadingMessage.innerHTML = "";
                }
            }
        } else {
            await submitQuery(username, undefined, undefined, undefined);
        }



    }

}

async function processAndUploadFile(image) {
    return new Promise((resolve, reject) => {
        let processedFile;
        const reader = new FileReader();

        reader.onload = (ev) => {
            if (!ev.target || !ev.target.result || typeof ev.target.result !== 'string') {
                reject(new Error('Failed to read file, file may be corrupt'));
            } else {

                const base64String = ev.target.result.toString();

                processedFile = {
                    title: image.name,
                    mime: image.type,
                    base64Data: base64String.split(',')[1]
                }

                resolve(processedFile);

            }
        }

        reader.onerror = () => reject(new Error("Error reading file, file may be corrupt"));

        reader.readAsDataURL(image);
    })

}

async function uploadFile(data) {
    const req = await fetch("upload", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `${data.title};${data.mime};${data.base64Data}`
    })

    if (!req.ok) {
        console.log(await req.body());
    } else {
        const res = await req.json();
        return res;
    }

}

async function submitQuery(username, fileType, fileLink, fileId) {
    const content = document.getElementById("postContent").value;

    const newPost = {
        author: username,
        timestamp: new Date().toLocaleString(),
        content: content,
        fileLink: fileLink === null && fileLink === undefined ? null : fileLink,
        fileType: fileType === null && fileType === undefined ? null : fileType,
        fileId: fileId === null && fileType === undefined ? null : fileId
    }

    const req = await fetch("query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `insert into posts value ${JSON.stringify(newPost)}`
    })

    if (!req.ok) {
        alert("Failed to create post");
    } else {
        const res = await req.json();

        redirect(`post.html?id=${res.id}`);

    }
}

checkAuth();