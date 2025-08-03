async function hydrateForm() {
    const urlParams = new URLSearchParams(window.location.search);
    const userId = urlParams.get('id');
    const numPosts = await getNumberPosts(userId);

    const req = await fetch("query", {
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

            const container = document.getElementById("profile");

            container.innerHTML = `
                <h1>👤 Profile</h1>
                <div class="profile-header">
                    <img class="profile-avatar" src="${profile.profilePic}"/>
                    <h2>${profile.username}</h2>
                    <p style="color: #666; margin-bottom: 1rem;">${profile.userDesc}</p>
                    <div class="profile-stats">
                        <div class="stat">
                            <div class="stat-number">${numPosts}</div>
                            <div class="stat-label">Posts</div>
                        </div>
                    </div>
                    ${getSignedInId() === profile.userId ? (`<button class="btn" style="margin-top: 1rem;" onclick="redirect('editProfile.html')">Edit Profile</button>`) : ``}
                </div>
            `

            document.title = `${profile.username} - MCSocial`

        }

    }

}

async function getNumberPosts(userId) {
    const req = await fetch("query", {
        method: "POST",
        headers: {
            'Authorization': getAuthToken()
        },
        body: `select * from posts where userId=${userId}`
    });

    if (!req.ok) {
        return 0;
    } else {
        const res = await req.json();
        return res.length;
    }

}

async function init() {
    await hydrateForm();
}

init();