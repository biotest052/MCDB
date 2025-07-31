async function hydrateForm() {
    const userId = getSignedInId();

    if (userId == 0) {
        redirect("login.html");
        return;
    }

    const req = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `select * from profiles where userId=${userId}`
    })

    if (!req.ok) {
        console.log("Something went wrong! " + req.body())
    } else {
        const res = await req.json();

        console.log(JSON.stringify(res));

        if (res.length !== 0) {
            const profile = res[0];

            const container = document.getElementById("edit-profile");

            container.innerHTML = `
                <h1>⚙️ Edit Profile</h1>
                <form>
                    <div class="form-group">
                        <label for="editBio">Bio</label>
                        <textarea id="editBio" rows="3">${profile.userDesc}</textarea>
                    </div>
                    <img id="previewPfp" class="profile-avatar" src="${profile.profilePic}"/>
                    <h2 style="text-align: center;">${profile.username}</h2>
                    <div class="form-group">
                        <label for="imageSelect">Profile Picture</label>
                        <input type="file" id="imageSelect" accept="image/*" onchange="handleImageSelect()">
                    </div>
                    <p style="color: red;" id="loadingMessage"></p>
                    <div style="display: flex; gap: 1rem;">
                        <button type="button" class="btn" onclick="submitUpdates()">Save Changes</button>
                        <button type="button" class="btn btn-secondary" onclick="showPage('profile')">Cancel</button>
                    </div>
                </form>
            `

        }

    }

}

async function submitUpdates() {
    const userId = getSignedInId();
    if (userId != 0) {
        let newPfp;

        const imageSelector = document.getElementById("imageSelect");
        if (imageSelector.files.length > 0) {
            const file = imageSelector.files[0];
            if ((file.size / 1000000) > 10) {
                alert("Images cannot be larger than 10 MB!");
            } else {
                const loadingMessage = document.getElementById("loadingMessage");
                loadingMessage.innerHTML = `Uploading new profile picture, please wait...<br/><br/>`
                try {
                    const processRes = await processAndUploadFile(imageSelector.files[0]);
                    const uploadRes = await uploadFile(processRes);
                    newPfp = uploadRes;
                    await submitQuery(userId, newPfp);
                    loadingMessage.innerHTML = ``
                } catch (error) {
                    alert(error);
                    loadingMessage.innerHTML = "";
                }
            }
        } else {
            submitQuery(userId, undefined);
        }


    } else {
        redirect("index.html");
    }

}

async function processAndUploadFile(image) {
    return new Promise((resolve, reject) => {
        let processedImage;
        const reader = new FileReader();

        reader.onload = (ev) => {
            if (!ev.target || !ev.target.result || typeof ev.target.result !== 'string') {
                reject(new Error('Failed to read image'));
            } else {

                const base64String = ev.target.result.toString();

                processedImage = {
                    title: image.name,
                    mime: image.type,
                    base64Data: base64String.split(',')[1]
                }

                resolve(processedImage);

            }
        }

        reader.onerror = () => reject(new Error("Error reading image"));

        reader.readAsDataURL(image);
    })

}

async function uploadFile(data) {
    const req = await fetch("http://localhost:8000/upload", {
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
        return res.link;
    }

}

function handleImageSelect() {

    const imageSelector = document.getElementById("imageSelect");
    if (imageSelector.files.length > 0) {
        const img = document.getElementById("previewPfp");

        img.src = URL.createObjectURL(imageSelector.files[0]);

    }

}

async function submitQuery(userId, newPfp) {
    const bio = document.getElementById('editBio').value;

    const req = await fetch("http://localhost:8000/query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `select * from profiles where userId=${userId}`
    })

    if (!req.ok) {
        console.log("Something went wrong! " + req.body())
    } else {
        const res = await req.json();

        console.log(JSON.stringify(res));

        if (res.length !== 0) {
            const currentProfile = res[0];

            const newProfile = {
                id: currentProfile.id,
                userId: currentProfile.userId,
                username: currentProfile.username,
                userDesc: bio,
                profilePic: newPfp !== null && newPfp !== undefined ? newPfp : currentProfile.profilePic
            }

            const nReq = await fetch("http://localhost:8000/query", {
                method: "POST",
                body: `update ${currentProfile.id} in profiles set ${JSON.stringify(newProfile)}`
            })

            if (!nReq.ok) {
                alert("Failed to update profile!");
                console.log(await nReq.body());
            } else {
                redirect(`profile.html?id=${currentProfile.userId}`)
            }

        }
    }
}

async function init() {
    await hydrateForm();
}

init();